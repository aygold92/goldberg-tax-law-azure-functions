package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.*
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.util.asCurrency
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.*

class StatementServiceTest : DatabaseTest() {

    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val transactionService = TransactionService(db)
    private val statementService = StatementService(transactionService, bankStatementVerifier, db)
    private val classificationService = ClassificationService(db)

    private val clientService = ClientService(db)
    private val fileService = FileService(db)

    // Shared upstream fixtures — recreated before each test.
    // insertBankStatementWithTransactions is under test here, so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID
    private lateinit var classification: Classification

    @BeforeAll
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        val fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
        )
        val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
        classification = classificationService.loadClassification(infos.single().classificationId)
    }

    @AfterEach
    fun clearStatements() {
        db.txnSafe { BankStatementsTable.deleteAll() }
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list, update, then delete`() {
            val txDetails = EntityValues.newTransactionDetails()
            val statementDetails = EntityValues.newStatementDetails()
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = statementDetails,
                    transactions = listOf(txDetails),
                )
            )

            val loaded = statementService.loadBankStatement(statementId)
            assertThat(loaded).entityCompare().isEqualTo(newStatement())
            assertThat(loaded.statementId).isEqualTo(statementId)

            // listStatements — verify summary appears with correct fields
            val statementSummaries = statementService.listBankStatements(clientId)
            assertThat(statementSummaries).hasSize(1)
            assertThat(statementSummaries.single()).entityCompare()
                .isEqualTo(EntityValues.newStatementSummary(numTransactions = 1, totalIncomeCredits = 500.asCurrency()))

            // Update
            val updatedDetails = EntityValues.newStatementDetails(
                statementId = statementId,
                accountNumber = "5678",
                date = "02/28/2024",
                beginningBalance = 1500.asCurrency(),
                endingBalance = 2000.asCurrency(),
                batesStamps = mapOf(2 to "AG-99999"),
            )
            statementService.updateBankStatement(updatedDetails)

            // loadStatement after update — verify all fields reflect the change
            val afterUpdateLoaded = statementService.loadBankStatement(statementId)

            assertThat(afterUpdateLoaded).entityCompare().isEqualTo(newStatement(
                statementDetails = updatedDetails,
            ))

            // listStatements after update
            val summariesAfterUpdate = statementService.listBankStatements(clientId)
            assertThat(summariesAfterUpdate).hasSize(1)
            assertThat(summariesAfterUpdate.single()).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                numTransactions = 1,
                statementDetails = updatedDetails,
                totalIncomeCredits = 500.asCurrency()
            ))

            // Delete
            statementService.deleteBankStatement(statementId)

            // loadStatement after delete throws
            assertThatThrownBy { statementService.loadBankStatement(statementId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // listStatements after delete is empty
            assertThat(statementService.listBankStatements(clientId)).isEmpty()
        }

        @Test
        fun `test timing`() {
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = classification, transactions = emptyList())
            )

            Thread.sleep(10)
            val loaded = statementService.loadBankStatement(statementId)
            val creationTime = loaded.statementDetails.createdAt
            assertTimeIsDuringTest(creationTime)
            assertThat(loaded.statementDetails.updatedAt).isEqualTo(creationTime)

            Thread.sleep(5)
            statementService.updateBankStatement(EntityValues.newStatementDetails(statementId = statementId))

            val afterUpdate = statementService.loadBankStatement(statementId)
            assertTimeInWindow(afterUpdate.statementDetails.updatedAt, creationTime)
            assertThat(afterUpdate.statementDetails.createdAt).isEqualTo(creationTime)
        }

        @Test
        fun `suspicious statement shows reasons on load and list, and clears after balance is corrected`() {
            val txns = listOf(
                EntityValues.newTransactionDetails(amount = 500.asCurrency()),
                EntityValues.newTransactionDetails(amount = (-200).asCurrency()),
            )
            // endingBalance wrong: 1000 + 300 != 1000
            val suspiciousDetails = EntityValues.newStatementDetails(
                beginningBalance = 1000.asCurrency(),
                endingBalance = 1000.asCurrency(),
            )
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = suspiciousDetails,
                    transactions = txns,
                )
            )

            val expectedSuspiciousReasons = bankStatementVerifier.getSuspiciousReasons(suspiciousDetails, txns, classification)
            assertThat(expectedSuspiciousReasons).isNotEmpty()

            // load: suspicious reasons present
            val loaded = statementService.loadBankStatement(statementId)
            assertThat(loaded).entityCompare().isEqualTo(EntityValues.newStatement(
                classification = classification,
                statementDetails = suspiciousDetails,
                suspiciousReasons = expectedSuspiciousReasons,
                transactions = txns,
            ))

            // list: same suspicious reasons in summary
            val summaries = statementService.listBankStatements(clientId)
            assertThat(summaries).hasSize(1)
            assertThat(summaries.single()).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                classification = classification,
                statementDetails = suspiciousDetails,
                suspiciousReasons = expectedSuspiciousReasons,
                totalSpending = (-200).asCurrency(),
                totalIncomeCredits = 500.asCurrency(),
                numTransactions = 2,
            ))

            // fix the balance: 1000 + 300 = 1300
            val fixedDetails = EntityValues.newStatementDetails(
                statementId = statementId,
                beginningBalance = 1000.asCurrency(),
                endingBalance = 1300.asCurrency(),
            )
            statementService.updateBankStatement(fixedDetails)

            // load after fix: no suspicious reasons
            val loadedAfterFix = statementService.loadBankStatement(statementId)
            assertThat(loadedAfterFix).entityCompare().isEqualTo(EntityValues.newStatement(
                classification = classification,
                statementDetails = fixedDetails,
                suspiciousReasons = emptyList(),
                transactions = txns,
            ))

            // list after fix: no suspicious reasons
            val summariesAfterFix = statementService.listBankStatements(clientId)
            assertThat(summariesAfterFix).hasSize(1)
            assertThat(summariesAfterFix.single()).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                classification = classification,
                statementDetails = fixedDetails,
                suspiciousReasons = emptyList(),
                totalSpending = (-200).asCurrency(),
                totalIncomeCredits = 500.asCurrency(),
                numTransactions = 2,
            ))
        }
    }

    @Nested
    inner class InsertStatement {

        @Test
        fun `statement with no transactions inserts successfully`() {
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(),
                    transactions = emptyList(),
                )
            )
            assertThat(statementId).isNotNull()
        }

        @Test
        fun `account number should support full-length values`() {
            // NOTE: BankStatementsTable.accountNumber is CHAR(4) which is too restrictive.
            // Real account numbers are typically 10–17 digits. This test documents the expected
            // behaviour — full account numbers should be accepted without truncation.
            // This test will fail until the column type is widened (e.g. VARCHAR(20)).
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(
                        accountNumber = EntityValues.DEFAULT_ACCOUNT_NUMBER, // "1234567890" — 10 chars
                    ),
                    transactions = emptyList(),
                )
            )
            assertThat(statementId).isNotNull()
            // Verify it round-trips correctly — if truncated, this would not equal the input
            val loaded = statementService.loadBankStatement(statementId)
            assertThat(loaded.accountNumber).isEqualTo(EntityValues.DEFAULT_ACCOUNT_NUMBER)
        }
    }

    @Nested
    inner class LoadStatement {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy { statementService.loadBankStatement(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateBankStatement {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            val unknownDetails = EntityValues.newStatementDetails(statementId = UUID.randomUUID())
            assertThatThrownBy { statementService.updateBankStatement(unknownDetails) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class DeleteBankStatement {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy { statementService.deleteBankStatement(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class CascadeDelete {

        @Test
        fun `deleting the parent file cascades to statements`() {
            val localClientId = clientService.insertClient("cascade-test-client", UUID.randomUUID())
            val localFileId = fileService.insertFile(
                EntityValues.newInputFile(client = EntityValues.newClient(clientId = localClientId)),
                UUID.randomUUID(),
            )
            val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = localFileId))
            val localClassification = classificationService.loadClassification(infos.single().classificationId)
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = localClassification, transactions = emptyList())
            )

            fileService.deleteInputFile(localFileId)

            assertThatThrownBy { statementService.loadBankStatement(statementId) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class ListStatements {

        @Test
        fun `returns 3 statements with suspicious reasons, missing checks, spending, and income`() {
            val fileId = classification.inputFile.info.fileId

            // clsf1 shared by stmt1 + stmt2; clsf2 used by stmt3
            val (clsf1, clsf2) = classificationService.loadClassifications(
                classificationService.insertClassifications(
                    EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(10)),
                        EntityValues.newClassifiedPages(pages = setOf(11)),

                    ))
                ).map { it.classificationId }.toSet()
            )

            // stmt1: balance wrong (1000 + 200 != 1000), suspicious transaction (null description), missing check 1001
            val stmt1Details = EntityValues.newStatementDetails(
                beginningBalance = 1000.asCurrency(),
                endingBalance = 1000.asCurrency(),
            )
            val stmt1Txns = listOf(
                EntityValues.newTransactionDetails(amount = 500.asCurrency()),
                EntityValues.newTransactionDetails(amount = (-200).asCurrency(), description = null),
                EntityValues.newTransactionDetails(amount = (-100).asCurrency(), checkNumber = 1001),
            )
            val stmtId1 = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = clsf1, statementDetails = stmt1Details, transactions = stmt1Txns)
            )

            // stmt2: balance wrong (500 + 0 != 400), missing check 2001
            val stmt2Details = EntityValues.newStatementDetails(
                beginningBalance = 500.asCurrency(),
                endingBalance = 400.asCurrency(),
            )
            val stmt2Txns = listOf(
                EntityValues.newTransactionDetails(amount = 200.asCurrency()),
                EntityValues.newTransactionDetails(amount = (-150).asCurrency()),
                EntityValues.newTransactionDetails(amount = (-50).asCurrency(), checkNumber = 2001),
            )
            val stmtId2 = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = clsf1, statementDetails = stmt2Details, transactions = stmt2Txns)
            )

            // stmt3: balance correct (100 + 50 = 150), no missing checks
            val stmt3Details = EntityValues.newStatementDetails(
                beginningBalance = 100.asCurrency(),
                endingBalance = 150.asCurrency(),
            )
            val stmt3Txns = listOf(
                EntityValues.newTransactionDetails(amount = 100.asCurrency()),
                EntityValues.newTransactionDetails(amount = (-50).asCurrency()),
            )
            val stmtId3 = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = clsf2, statementDetails = stmt3Details, transactions = stmt3Txns)
            )

            val summaries = statementService.listBankStatements(clientId)
            assertThat(summaries).hasSize(3)

            val summary1 = summaries.first { it.statementDetails.statementId == stmtId1 }
            val summary2 = summaries.first { it.statementDetails.statementId == stmtId2 }
            val summary3 = summaries.first { it.statementDetails.statementId == stmtId3 }

            assertThat(summary1).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                classification = clsf1,
                statementDetails = stmt1Details,
                suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(stmt1Details, stmt1Txns, clsf1),
                missingChecks = setOf("1001"),
                totalSpending = (-300).asCurrency(),
                totalIncomeCredits = 500.asCurrency(),
                numTransactions = 3,
            ))
            assertThat(summary2).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                classification = clsf1,
                statementDetails = stmt2Details,
                suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(stmt2Details, stmt2Txns, clsf1),
                missingChecks = setOf("2001"),
                totalSpending = (-200).asCurrency(),
                totalIncomeCredits = 200.asCurrency(),
                numTransactions = 3,
            ))
            assertThat(summary3).entityCompare().isEqualTo(EntityValues.newStatementSummary(
                classification = clsf2,
                statementDetails = stmt3Details,
                suspiciousReasons = emptyList(),
                totalSpending = (-50).asCurrency(),
                totalIncomeCredits = 100.asCurrency(),
                numTransactions = 2,
            ))
        }

        @Test
        fun `returns correct transaction count and spending and income aggregations`() {
            val income = EntityValues.newTransactionDetails(amount = 1000.0.toBigDecimal())
            val spending = EntityValues.newTransactionDetails(amount = (-300.0).toBigDecimal())
            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(
                        beginningBalance = 1000.0.toBigDecimal(),
                        endingBalance = 1700.0.toBigDecimal(),
                    ),
                    transactions = listOf(income, spending),
                )
            )

            val summaries = statementService.listBankStatements(clientId)
            assertThat(summaries).hasSize(1)
            val summary = summaries.single()
            assertThat(summary.numTransactions).isEqualTo(2)
            assertThat(summary.totalSpending).isEqualByComparingTo((-300.0).toBigDecimal())

            // NOTE: listStatements has a bug — both the totalSpending and totalIncome aggregation
            // expressions are aliased "totalSpending". When Exposed reads row[totalIncome] it
            // returns the totalSpending value instead of the real income sum.
            // This assertion will fail until the totalIncome alias is given a distinct name.
            assertThat(summary.totalIncomeCredits).isEqualByComparingTo(1000.0.toBigDecimal())
        }

        @Test
        fun `does not return statements belonging to other clients`() {
            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(),
                    transactions = emptyList(),
                )
            )
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())

            assertThat(statementService.listBankStatements(otherClientId)).isEmpty()
        }
    }
}
