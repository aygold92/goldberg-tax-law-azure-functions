package com.goldberg.law.agent

import com.goldberg.law.agent.model.output.AccountReviewRequired
import com.goldberg.law.agent.model.output.BatesReport
import com.goldberg.law.agent.model.output.BatesSequence
import com.goldberg.law.agent.model.output.CheckExtractionOutput
import com.goldberg.law.agent.model.output.ExtractedAccount
import com.goldberg.law.agent.model.output.ExtractedCheck
import com.goldberg.law.agent.model.output.ExtractedTransaction
import com.goldberg.law.agent.model.output.SplitterBank
import com.goldberg.law.agent.model.output.SplitterOutput
import com.goldberg.law.agent.model.output.SplitterReviewItem
import com.goldberg.law.agent.model.output.StatementBoundary
import com.goldberg.law.agent.model.output.StatementExtractionOutput
import com.goldberg.law.agent.model.output.StatementReviewRequired
import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.DEFAULT_STORAGE_LOCATION
import com.goldberg.law.entity.ReviewNote
import com.goldberg.law.entity.ReviewStatus
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.util.asCurrency
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.assertj.core.api.Assertions.entry
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.reset
import org.mockito.kotlin.times
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

class AgentResultSaverTest : DatabaseTest() {

    private val launcher: AgentSessionLauncher = mock()
    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val classificationService = ClassificationService(db)
    private val transactionService = TransactionService(db)
    private val statementService = StatementService(transactionService, BankStatementVerifier(TransactionVerifier()), db)
    private val checkService = CheckService(db)
    private val dataManager: AzureStorageDataManager = mock()
    private val saver =
        AgentResultSaver(launcher, fileService, classificationService, statementService, checkService, dataManager)

    private val splitterSession = "sess_split_1"
    private val extractionSession = "sess_extract_1"

    /** Stands in for the agent's `result.json`: the archive must keep it byte-for-byte, unknown fields and all. */
    private val RESULT_JSON = """{"bank_id":"bank_of_america","accounts":[],"note":"Café Dupré","schema_version":"v99"}"""

    private lateinit var fileId: UUID

    @BeforeEach
    fun setupFile() {
        // PER_CLASS lifecycle: rows and mock interactions would otherwise carry over between tests
        db.txnSafe { ClientsTable.deleteAll() }
        reset(launcher, dataManager)
        whenever(dataManager.saveAgentOutput(any(), any())).thenReturn(DEFAULT_STORAGE_LOCATION)
        val clientId = clientService.insertClient("client-${UUID.randomUUID()}", UUID.randomUUID())
        fileId = fileService.insertFile(
            EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file-${UUID.randomUUID()}.pdf", numPages = 12),
            ),
            UUID.randomUUID(),
        )
        fileService.updateSplitterSession(fileId, splitterSession, "file_abc")
    }

    private fun givenResult(agent: ManagedAgent, output: Any, sessionId: String, json: String = RESULT_JSON) {
        whenever(launcher.fetchResult(sessionId)).thenReturn(
            AgentSessionResult(sessionId, agent, AgentSessionResult.Status.COMPLETED, output = output, outputJson = json)
        )
    }

    /** A statement classification over [pages], already tied to [extractionSession]. */
    private fun givenExtractionClassification(
        pages: Set<Int>,
        type: String = "bank_of_america",
        batesStamps: Map<Int, String> = emptyMap(),
    ): UUID {
        val info = classificationService.insertClassifications(
            EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                EntityValues.newClassifiedPages(pages, type, batesStamps = batesStamps)
            ))
        ).single()
        classificationService.updateExtractionSession(info.classificationId, extractionSession)
        return info.classificationId
    }

    @Nested
    inner class SplitterResults {

        @Test
        fun `boundaries and check pages become the file's classifications, with bank names and bates stamps`() {
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(
                banks = mapOf(
                    "bank_of_america" to SplitterBank("Bank of America", "memory"),
                    "chase_cc" to SplitterBank("Chase", "discovered"),
                ),
                boundaries = listOf(StatementBoundary(1, 3, "bank_of_america"), StatementBoundary(4, 6, "chase_cc")),
                checkPages = listOf(2, 3),
                bates = BatesReport(sequences = listOf(BatesSequence(1, 6, "AG-000001", "AG-000006"))),
            ), splitterSession)

            val result = saver.save(splitterSession)

            assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(result.fileId).isEqualTo(fileId)
            assertThat(result.classificationIds).hasSize(3)

            // Loaded in page order, so the check pages (2-3) come between the two statements
            val classifications = classificationService.loadClassifications(fileId)
            assertThat(classifications.map { it.classificationType })
                .containsExactly("bank_of_america", DocumentType.CheckTypes.CHECKS, "chase_cc")
            assertThat(classifications.map { it.pages }).containsExactly(setOf(1, 2, 3), setOf(2, 3), setOf(4, 5, 6))
            assertThat(classifications.map { it.bankName }).containsExactly("Bank of America", null, "Chase")
            // Each classification keeps the stamps for its own pages
            assertThat(classifications.map { it.info.batesStamps.keys }).containsExactly(setOf(1, 2, 3), setOf(2, 3), setOf(4, 5, 6))
            assertThat(classifications.first().info.batesStamps).containsEntry(1, "AG-000001")
            assertThat(classifications.last().info.batesStamps).containsEntry(6, "AG-000006")
            // The bank id's shape decides the type the rest of the app sees
            assertThat(classifications.map { it.documentType }).containsExactly(
                DocumentType.BANK, DocumentType.CHECK, DocumentType.CREDIT_CARD,
            )
        }

        @Test
        fun `review notes with a pending status, unassigned pages and each bank's pattern source are recorded`() {
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(
                banks = mapOf(
                    "bank_of_america" to SplitterBank("Bank of America", "memory"),
                    "chase_cc" to SplitterBank("Chase", "discovered"),
                ),
                boundaries = listOf(StatementBoundary(1, 3, "bank_of_america"), StatementBoundary(4, 6, "chase_cc")),
                checkPages = listOf(7),
                unassignedPages = listOf(9, 8),
                reviewRequired = listOf(
                    SplitterReviewItem("the seam could be 3/4 or 4/5", listOf(3, 4)),
                    SplitterReviewItem("pages 10-12 name no institution"),
                ),
            ), splitterSession)

            saver.save(splitterSession)

            val file = fileService.loadFile(fileId).info
            assertThat(file.reviewNotes).containsExactly(
                ReviewNote("the seam could be 3/4 or 4/5", listOf(3, 4)),
                ReviewNote("pages 10-12 name no institution"),
            )
            assertThat(file.reviewStatus).isEqualTo(ReviewStatus.PENDING)
            assertThat(file.unassignedPages).containsExactly(8, 9)
            // Check pages have no bank, so no source
            assertThat(classificationService.loadClassifications(fileId).map { it.bankSource })
                .containsExactly("memory", "discovered", null)
        }

        @Test
        fun `a splitter run that found nothing still records what it flagged`() {
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(
                reviewRequired = listOf(SplitterReviewItem("pages 1-12 look like a statement but no institution is named")),
                unassignedPages = (1..12).toList(),
            ), splitterSession)

            saver.save(splitterSession)

            assertThat(fileService.loadFile(fileId).info.reviewNotes)
                .containsExactly(ReviewNote("pages 1-12 look like a statement but no institution is named"))
            assertThat(fileService.loadFile(fileId).info.reviewStatus).isEqualTo(ReviewStatus.PENDING)
            assertThat(fileService.loadFile(fileId).info.unassignedPages).isEqualTo((1..12).toList())
        }

        @Test
        fun `re-running the splitter with override replaces the file's earlier classifications`() {
            classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(setOf(1, 2, 3), "an_old_guess"),
                ))
            )
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(
                banks = mapOf("bank_of_america" to SplitterBank("Bank of America", "memory")),
                boundaries = listOf(StatementBoundary(1, 2, "bank_of_america")),
            ), splitterSession)

            saver.save(splitterSession, override = true)

            val classifications = classificationService.loadClassifications(fileId)
            assertThat(classifications).hasSize(1)
            assertThat(classifications.single().classificationType).isEqualTo("bank_of_america")
            assertThat(classifications.single().pages).isEqualTo(setOf(1, 2))
        }

        @Test
        fun `a file that is already classified is left alone without override`() {
            val existing = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(setOf(1, 2, 3), "an_edited_guess"),
                ))
            ).single()
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(
                banks = mapOf("bank_of_america" to SplitterBank("Bank of America", "memory")),
                boundaries = listOf(StatementBoundary(1, 2, "bank_of_america")),
                reviewRequired = listOf(SplitterReviewItem("not recorded: the file was already saved")),
            ), splitterSession)

            val result = saver.save(splitterSession)

            assertThat(result.alreadySaved).isTrue()
            assertThat(fileService.loadFile(fileId).info.reviewNotes).isEmpty()
            assertThat(fileService.loadFile(fileId).info.reviewStatus).isNull()
            assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(result.fileId).isEqualTo(fileId)
            assertThat(result.classificationIds).containsExactly(existing.classificationId)

            // The manual edit survives: same row, same type, same pages
            val classifications = classificationService.loadClassifications(fileId)
            assertThat(classifications.map { it.classificationId }).containsExactly(existing.classificationId)
            assertThat(classifications.single().classificationType).isEqualTo("an_edited_guess")
            assertThat(classifications.single().pages).isEqualTo(setOf(1, 2, 3))
        }

        @Test
        fun `a splitter run that found nothing leaves the existing classifications alone`() {
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            givenResult(ManagedAgent.SPLITTER, SplitterOutput(), splitterSession)

            val result = saver.save(splitterSession, override = true)

            assertThat(result.classificationIds).isEmpty()
            assertThat(classificationService.loadClassifications(fileId)).hasSize(1)
        }
    }

    @Nested
    inner class StatementResults {

        @Test
        fun `each account becomes a statement with its transactions`() {
            val classificationId = givenExtractionClassification(setOf(1, 2, 3), batesStamps = mapOf(1 to "AG-001", 2 to "AG-002"))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                accounts = listOf(
                    ExtractedAccount(
                        accountNumber = "000123456789",
                        beginningBalance = BigDecimal("500"),
                        endingBalance = BigDecimal("1000.5"),
                        feesCharged = BigDecimal("12"),
                        transactions = listOf(
                            ExtractedTransaction(LocalDate.of(2024, 1, 5), "deposit", null, BigDecimal("600"), 1),
                            ExtractedTransaction(LocalDate.of(2024, 1, 6), "check", "1042", BigDecimal("-99.5"), 2),
                        ),
                    ),
                    ExtractedAccount(
                        accountNumber = "9876",
                        beginningBalance = BigDecimal("10"),
                        endingBalance = BigDecimal("10"),
                    ),
                ),
            ), extractionSession)

            val result = saver.save(extractionSession)

            assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(result.classificationIds).containsExactly(classificationId)
            assertThat(result.statementIds).hasSize(2)

            val statements = result.statementIds.map { statementService.loadBankStatement(it) }
            val main = statements.single { it.accountNumber == "6789" } // stored as the last 4 digits
            assertThat(main.date).isEqualTo("2024-01-31")
            assertThat(main.beginningBalance).isEqualTo(500.asCurrency())
            assertThat(main.endingBalance).isEqualTo(BigDecimal("1000.50"))
            assertThat(main.feesCharged).isEqualTo(12.asCurrency())
            // The stamps stay on the classification; the statement's own column is the Azure pipeline's
            assertThat(main.batesStamps).isEmpty()
            assertThat(main.classification.info.batesStamps).containsExactlyEntriesOf(mapOf(1 to "AG-001", 2 to "AG-002"))

            assertThat(main.transactions.map { it.description }).containsExactly("deposit", "check")
            assertThat(main.transactions.map { it.statementIndex }).containsExactly(0, 1)
            assertThat(main.transactions.map { it.filePageNumber }).containsExactly(1, 2)
            assertThat(main.transactions.map { it.date }).containsExactly("2024-01-05", "2024-01-06")
            assertThat(main.transactions.map { it.amount }).containsExactly(600.asCurrency(), BigDecimal("-99.50"))
            assertThat(main.transactions.map { it.checkNumber }).containsExactly(null, 1042)
        }

        @Test
        fun `every summary figure, daily balance, error and review flag is stored`() {
            givenExtractionClassification(setOf(1, 2, 3))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                statementStart = LocalDate.of(2024, 1, 1),
                errors = listOf("summary_missing_fields"),
                reviewRequired = StatementReviewRequired(
                    fields = listOf("statement_date"),
                    notes = listOf("the interest line could belong to either account"),
                ),
                accounts = listOf(ExtractedAccount(
                    accountName = "Business Advantage Checking",
                    accountNumber = "4460 5473 8649",
                    beginningBalance = BigDecimal("215871.68"),
                    endingBalance = BigDecimal("229798.86"),
                    totalCredits = BigDecimal("250678.39"),
                    totalDebits = BigDecimal("236718.71"),
                    txnCountCredit = 5,
                    txnCountDebit = 47,
                    txnCount = 52,
                    checksTotal = BigDecimal("0.00"),
                    feesCharged = BigDecimal("32.5"),
                    interestReceived = BigDecimal("1.07"),
                    interestCharged = BigDecimal("0"),
                    // Out of order, as a map from the agent may be
                    dailyBalances = mapOf(
                        LocalDate.of(2024, 1, 5) to BigDecimal("365821.68"),
                        LocalDate.of(2024, 1, 4) to BigDecimal("365871.68"),
                    ),
                    summaryArithmeticFields = listOf("total_credits", "total_debits", "fees_charged", "cash_advances"),
                    otherDebits = mapOf("cash_advances" to BigDecimal("10")),
                    errors = listOf("daily_ledger_mismatch"),
                    reviewRequired = AccountReviewRequired(
                        date = listOf(1, 2),
                        amt = listOf(1, 2),
                        desc = listOf(0),
                        check = listOf(2, 7), // 7 is past the end, so it's dropped
                        fields = listOf("beginning_balance"),
                        notes = listOf("rows 1-2 appear misaligned"),
                    ),
                    transactions = listOf(
                        ExtractedTransaction(LocalDate.of(2024, 1, 4), "alndmz", null, BigDecimal("150000"), 1),
                        ExtractedTransaction(LocalDate.of(2024, 1, 5), "monthly fee", null, BigDecimal("-50"), 2, countedIn = "fees_charged"),
                        ExtractedTransaction(LocalDate.of(2024, 1, 5), "cash advance", "1042", BigDecimal("-10"), 2, countedIn = "cash_advances"),
                        ExtractedTransaction(LocalDate.of(2024, 1, 6), "deposit", null, BigDecimal("5"), 3),
                    ),
                )),
            ), extractionSession)

            val result = saver.save(extractionSession)

            val statement = statementService.loadBankStatement(result.statementIds.single())
            assertThat(statement.statementDetails).usingRecursiveComparison()
                .ignoringFields("statementId", "createdAt", "updatedAt")
                .isEqualTo(StatementDetails(
                    statementId = UUID.randomUUID(),
                    date = "2024-01-31",
                    accountNumber = "8649",
                    beginningBalance = BigDecimal("215871.68"),
                    endingBalance = BigDecimal("229798.86"),
                    interestCharged = BigDecimal("0.00"),
                    feesCharged = BigDecimal("32.50"),
                    batesStamps = emptyMap(),
                    accountName = "Business Advantage Checking",
                    startDate = "2024-01-01",
                    totalCredits = BigDecimal("250678.39"),
                    totalDebits = BigDecimal("236718.71"),
                    checksTotal = BigDecimal("0.00"),
                    interestReceived = BigDecimal("1.07"),
                    txnCountCredit = 5,
                    txnCountDebit = 47,
                    txnCount = 52,
                    summaryArithmeticFields = listOf("totalCredits", "totalDebits", "feesCharged", "cash_advances"),
                    otherDebits = mapOf("cash_advances" to BigDecimal("10.00")),
                    // The statement-level error, field and note come first, then the account's own
                    agentErrors = listOf("summary_missing_fields", "daily_ledger_mismatch"),
                    reviewFields = listOf("date", "beginningBalance"),
                    reviewNotes = listOf("the interest line could belong to either account", "rows 1-2 appear misaligned"),
                    reviewStatus = ReviewStatus.PENDING,
                ))
            assertThat(statement.dailyBalances).containsExactly(
                entry("2024-01-04", BigDecimal("365871.68")),
                entry("2024-01-05", BigDecimal("365821.68")),
            )

            // A summary field is named as its property; an other-line label is kept as the agent wrote it
            assertThat(statement.transactions.map { it.countedIn }).containsExactly(null, "feesCharged", "cash_advances", null)

            // The index lists become flags on the rows they point at, named as the rows' properties
            assertThat(statement.transactions.map { it.reviewFields }).containsExactly(
                listOf("description"),
                listOf("date", "amount"),
                listOf("date", "checkNumber", "amount"),
                emptyList(),
            )
            assertThat(statement.transactions.map { it.reviewStatus })
                .containsExactly(ReviewStatus.PENDING, ReviewStatus.PENDING, ReviewStatus.PENDING, null)
        }

        @Test
        fun `what the agent flagged at statement level applies to every account, and an account with nothing flagged has no status`() {
            givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                errors = listOf("summary_missing_fields"),
                reviewRequired = StatementReviewRequired(fields = listOf("statement_start"), notes = listOf("which account the interest belongs to is unclear")),
                accounts = listOf(
                    ExtractedAccount(accountNumber = "1111", beginningBalance = null, endingBalance = null),
                    ExtractedAccount(accountNumber = "2222", beginningBalance = null, endingBalance = null),
                ),
            ), extractionSession)

            val statements = saver.save(extractionSession).statementIds.map { statementService.loadBankStatement(it).statementDetails }

            assertThat(statements).hasSize(2).allSatisfy {
                assertThat(it.agentErrors).containsExactly("summary_missing_fields")
                assertThat(it.reviewFields).containsExactly("startDate")
                assertThat(it.reviewNotes).containsExactly("which account the interest belongs to is unclear")
                assertThat(it.reviewStatus).isEqualTo(ReviewStatus.PENDING)
            }
        }

        @Test
        fun `an account nothing was flagged on has no review status`() {
            givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                // An error alone doesn't ask for a review: it's resolved by fixing the data
                errors = listOf("balance_identity_failed"),
                accounts = listOf(ExtractedAccount(
                    accountNumber = "1111",
                    beginningBalance = null,
                    endingBalance = null,
                    transactions = listOf(ExtractedTransaction(LocalDate.of(2024, 1, 5), "deposit", null, BigDecimal("600"), 1)),
                )),
            ), extractionSession)

            val statement = statementService.loadBankStatement(saver.save(extractionSession).statementIds.single())

            assertThat(statement.statementDetails.agentErrors).containsExactly("balance_identity_failed")
            assertThat(statement.statementDetails.reviewFields).isEmpty()
            assertThat(statement.statementDetails.reviewNotes).isEmpty()
            assertThat(statement.statementDetails.reviewStatus).isNull()
            assertThat(statement.transactions.single().reviewFields).isEmpty()
            assertThat(statement.transactions.single().reviewStatus).isNull()
        }

        @Test
        fun `re-running extraction with override replaces the classification's earlier statements`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                accounts = listOf(ExtractedAccount(accountNumber = "1111", beginningBalance = null, endingBalance = null)),
            ), extractionSession)

            val first = saver.save(extractionSession)
            val second = saver.save(extractionSession, override = true)

            assertThat(second.alreadySaved).isFalse()
            assertThat(second.statementIds).hasSize(1)
            assertThat(second.statementIds).isNotEqualTo(first.statementIds) // rewritten, not kept
            assertThat(statementService.loadStatementIdsForClassification(classificationId))
                .containsExactlyElementsOf(second.statementIds)
        }

        @Test
        fun `a classification that already has statements is left alone without override`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, StatementExtractionOutput(
                bankId = "bank_of_america",
                statementDate = LocalDate.of(2024, 1, 31),
                accounts = listOf(ExtractedAccount(
                    accountNumber = "1111",
                    beginningBalance = null,
                    endingBalance = null,
                    transactions = listOf(ExtractedTransaction(LocalDate.of(2024, 1, 5), "deposit", null, BigDecimal("600"), 1)),
                )),
            ), extractionSession)

            val first = saver.save(extractionSession)
            // A manual correction made after the first save
            val saved = statementService.loadBankStatement(first.statementIds.single())
            statementService.updateBankStatement(saved.statementDetails.copy(endingBalance = BigDecimal("42.00")))

            val second = saver.save(extractionSession)

            assertThat(second.alreadySaved).isTrue()
            assertThat(second.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(second.classificationIds).containsExactly(classificationId)
            assertThat(second.statementIds).containsExactlyElementsOf(first.statementIds)

            // Same rows, and the edit survives
            assertThat(statementService.loadStatementIdsForClassification(classificationId))
                .containsExactlyElementsOf(first.statementIds)
            val reloaded = statementService.loadBankStatement(first.statementIds.single())
            assertThat(reloaded.endingBalance).isEqualTo(BigDecimal("42.00"))
            assertThat(reloaded.transactions).hasSize(1)
        }
    }

    @Nested
    inner class CheckResults {

        @Test
        fun `checks are saved against the classification with the file's bates stamp for their page`() {
            val classificationId = givenExtractionClassification(
                setOf(4, 5), DocumentType.CheckTypes.CHECKS, batesStamps = mapOf(4 to "AG-004", 5 to "AG-005"),
            )
            givenResult(ManagedAgent.CHECK_EXTRACTION, CheckExtractionOutput(
                checks = listOf(
                    ExtractedCheck(
                        page = 4,
                        checkNo = 1042,
                        acct = "000123456789",
                        date = LocalDate.of(2024, 1, 15),
                        amt = BigDecimal("250"),
                        payee = "John Doe",
                        memo = "rent",
                    ),
                    ExtractedCheck(page = 5, checkNo = null, acct = null, date = null, amt = null, payee = null, memo = null),
                ),
            ), extractionSession)

            val result = saver.save(extractionSession)

            assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(result.classificationIds).containsExactly(classificationId)
            assertThat(result.checkIds).hasSize(2)

            val checks = result.checkIds.map { checkService.loadCheck(it) }
            val first = checks.single { it.checkNumber == 1042 }
            assertThat(first.accountNumber).isEqualTo("6789")
            assertThat(first.date).isEqualTo("2024-01-15")
            assertThat(first.amount).isEqualTo(250.asCurrency())
            assertThat(first.to).isEqualTo("John Doe")
            assertThat(first.description).isEqualTo("rent")
            // The stamps stay on the classification; the check's own column is the Azure pipeline's
            assertThat(first.batesStamp).isNull()
            assertThat(first.classification.info.batesStamps)
                .containsExactlyEntriesOf(mapOf(4 to "AG-004", 5 to "AG-005"))

            val empty = checks.single { it.checkNumber == null }
            assertThat(empty.amount).isNull()
        }

        @Test
        fun `each check keeps its page and review status, and unreadable pages go on the classification`() {
            val classificationId = givenExtractionClassification(setOf(4, 5, 6, 7), DocumentType.CheckTypes.CHECKS)
            givenResult(ManagedAgent.CHECK_EXTRACTION, CheckExtractionOutput(
                checks = listOf(
                    ExtractedCheck(page = 4, checkNo = 1042, acct = null, date = null, amt = BigDecimal("250"), payee = "John Doe", memo = null),
                    ExtractedCheck(page = 5, checkNo = 1043, acct = null, date = null, amt = BigDecimal("340"), payee = null, memo = null, reviewRequired = true),
                ),
                pagesWithNoChecks = listOf(7),
                unreadablePages = listOf(6),
            ), extractionSession)

            val result = saver.save(extractionSession)

            val checks = result.checkIds.map { checkService.loadCheck(it).checkDetails }.sortedBy { it.checkNumber }
            assertThat(checks.map { it.filePageNumber }).containsExactly(4, 5)
            assertThat(checks.map { it.reviewStatus }).containsExactly(null, ReviewStatus.PENDING)
            assertThat(classificationService.loadClassification(classificationId).info.unreadablePages).containsExactly(6)
        }

        @Test
        fun `a check run with no unreadable pages clears the classification's earlier ones`() {
            val classificationId = givenExtractionClassification(setOf(4), DocumentType.CheckTypes.CHECKS)
            classificationService.updateUnreadablePages(classificationId, listOf(4))
            givenResult(ManagedAgent.CHECK_EXTRACTION, CheckExtractionOutput(
                checks = listOf(ExtractedCheck(page = 4, checkNo = 1042, acct = null, date = null, amt = BigDecimal("250"), payee = "John Doe", memo = null)),
            ), extractionSession)

            saver.save(extractionSession)

            assertThat(classificationService.loadClassification(classificationId).info.unreadablePages).isEmpty()
        }

        @Test
        fun `a classification that already has checks is left alone without override`() {
            val classificationId = givenExtractionClassification(setOf(4), DocumentType.CheckTypes.CHECKS)
            givenResult(ManagedAgent.CHECK_EXTRACTION, CheckExtractionOutput(
                checks = listOf(ExtractedCheck(page = 4, checkNo = 1042, acct = null, date = null, amt = BigDecimal("250"), payee = "John Doe", memo = null)),
            ), extractionSession)

            val first = saver.save(extractionSession)
            val second = saver.save(extractionSession)

            assertThat(second.alreadySaved).isTrue()
            assertThat(second.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
            assertThat(second.classificationIds).containsExactly(classificationId)
            assertThat(second.checkIds).containsExactlyElementsOf(first.checkIds)
            assertThat(checkService.loadCheckIdsForClassification(classificationId))
                .containsExactlyElementsOf(first.checkIds)
        }

        @Test
        fun `re-running check extraction with override replaces the classification's earlier checks`() {
            val classificationId = givenExtractionClassification(setOf(4), DocumentType.CheckTypes.CHECKS)
            givenResult(ManagedAgent.CHECK_EXTRACTION, CheckExtractionOutput(
                checks = listOf(ExtractedCheck(page = 4, checkNo = 1042, acct = null, date = null, amt = BigDecimal("250"), payee = "John Doe", memo = null)),
            ), extractionSession)

            val first = saver.save(extractionSession)
            val second = saver.save(extractionSession, override = true)

            assertThat(second.alreadySaved).isFalse()
            assertThat(second.checkIds).hasSize(1)
            assertThat(second.checkIds).isNotEqualTo(first.checkIds) // rewritten, not kept
            assertThat(checkService.loadCheckIdsForClassification(classificationId))
                .containsExactlyElementsOf(second.checkIds)
        }
    }

    @Nested
    inner class UnfinishedSessions {

        @Test
        fun `a running session writes nothing`() {
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            whenever(launcher.fetchResult(splitterSession)).thenReturn(
                AgentSessionResult(splitterSession, ManagedAgent.SPLITTER, AgentSessionResult.Status.RUNNING)
            )

            val result = saver.save(splitterSession)

            assertThat(result.status).isEqualTo(AgentSessionResult.Status.RUNNING)
            assertThat(result.classificationIds).isEmpty()
            assertThat(classificationService.loadClassifications(fileId)).hasSize(1) // untouched
        }

        @Test
        fun `a failed session is reported with its error and writes nothing`() {
            whenever(launcher.fetchResult(splitterSession)).thenReturn(
                AgentSessionResult(splitterSession, ManagedAgent.SPLITTER, AgentSessionResult.Status.AGENT_ERROR, error = "no statements found")
            )

            val result = saver.save(splitterSession)

            assertThat(result.status).isEqualTo(AgentSessionResult.Status.AGENT_ERROR)
            assertThat(result.error).isEqualTo("no statements found")
            assertThat(classificationService.loadClassifications(fileId)).isEmpty()
        }

        @Test
        fun `a session from an agent whose output isn't stored is rejected`() {
            whenever(launcher.fetchResult(splitterSession)).thenReturn(
                AgentSessionResult(splitterSession, ManagedAgent.MEMORY_CONSOLIDATION, AgentSessionResult.Status.COMPLETED, output = "tidied up")
            )

            assertThatThrownBy { saver.save(splitterSession) }.isInstanceOf(IllegalArgumentException::class.java)
        }
    }

    @Nested
    inner class ArchivedResults {

        private fun modelLocationOf(classificationId: UUID) =
            classificationService.loadClassification(classificationId).info.modelLocation

        private fun statementOutput() = StatementExtractionOutput(
            bankId = "bank_of_america",
            statementDate = LocalDate.of(2024, 1, 31),
            accounts = listOf(ExtractedAccount(accountNumber = "1111", beginningBalance = null, endingBalance = null)),
        )

        @Test
        fun `a completed extraction archives result json verbatim and points the classification at it`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, statementOutput(), extractionSession)

            saver.save(extractionSession)

            val archived = argumentCaptor<String>()
            verify(dataManager).saveAgentOutput(any(), archived.capture())
            assertThat(archived.firstValue).isEqualTo(RESULT_JSON)
            assertThat(modelLocationOf(classificationId)).isEqualTo(DEFAULT_STORAGE_LOCATION)
        }

        @Test
        fun `a completed check extraction is archived too`() {
            val classificationId = givenExtractionClassification(setOf(4, 5), DocumentType.CheckTypes.CHECKS)
            givenResult(
                ManagedAgent.CHECK_EXTRACTION,
                CheckExtractionOutput(checks = listOf(ExtractedCheck(
                    page = 4,
                    checkNo = 1042,
                    acct = null,
                    date = null,
                    amt = BigDecimal("99.50"),
                    payee = "John Doe",
                    memo = null,
                ))),
                extractionSession,
            )

            saver.save(extractionSession)

            verify(dataManager).saveAgentOutput(any(), any())
            assertThat(modelLocationOf(classificationId)).isEqualTo(DEFAULT_STORAGE_LOCATION)
        }

        @Test
        fun `an extraction that produced no accounts is still archived, so it counts as analyzed`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            givenResult(
                ManagedAgent.STATEMENT_EXTRACTION,
                StatementExtractionOutput(bankId = "bank_of_america", statementDate = null, accounts = emptyList()),
                extractionSession,
            )

            saver.save(extractionSession)

            // Nothing to count in the statements table, so the archive is the only record the run finished
            assertThat(statementService.loadStatementIdsForClassification(classificationId)).isEmpty()
            assertThat(modelLocationOf(classificationId)).isEqualTo(DEFAULT_STORAGE_LOCATION)
        }

        @Test
        fun `the archive is written only after the statements are committed`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, statementOutput(), extractionSession)

            var statementsAtArchiveTime = -1
            whenever(dataManager.saveAgentOutput(any(), any())).thenAnswer {
                statementsAtArchiveTime = statementService.loadStatementIdsForClassification(classificationId).size
                DEFAULT_STORAGE_LOCATION
            }

            saver.save(extractionSession)

            // Otherwise a failed insert would leave the classification pointing at an analysis that isn't there
            assertThat(statementsAtArchiveTime).isEqualTo(1)
        }

        @Test
        fun `a session that didn't complete archives nothing and leaves the classification un-analyzed`() {
            val classificationId = givenExtractionClassification(setOf(1, 2))
            whenever(launcher.fetchResult(extractionSession)).thenReturn(
                AgentSessionResult(
                    extractionSession,
                    ManagedAgent.STATEMENT_EXTRACTION,
                    AgentSessionResult.Status.AGENT_ERROR,
                    error = "no statement in the page range",
                    outputJson = """{"error":"no statement in the page range"}""",
                )
            )

            saver.save(extractionSession)

            verify(dataManager, never()).saveAgentOutput(any(), any())
            assertThat(modelLocationOf(classificationId)).isNull()
        }

        @Test
        fun `a classification that was already saved is not re-archived`() {
            givenExtractionClassification(setOf(1, 2))
            givenResult(ManagedAgent.STATEMENT_EXTRACTION, statementOutput(), extractionSession)

            saver.save(extractionSession)
            val second = saver.save(extractionSession)

            assertThat(second.alreadySaved).isTrue()
            verify(dataManager, times(1)).saveAgentOutput(any(), any())
        }
    }
}
