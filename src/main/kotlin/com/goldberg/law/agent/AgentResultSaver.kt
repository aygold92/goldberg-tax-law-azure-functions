package com.goldberg.law.agent

import com.goldberg.law.agent.model.output.AccountReviewRequired
import com.goldberg.law.agent.model.output.CheckExtractionOutput
import com.goldberg.law.agent.model.output.ExtractedAccount
import com.goldberg.law.agent.model.output.SplitterOutput
import com.goldberg.law.agent.model.output.StatementExtractionOutput
import com.goldberg.law.agent.model.output.toPageStamps
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassifiedFile
import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.entity.ReviewNote
import com.goldberg.law.entity.ReviewStatus
import com.goldberg.law.entity.Statement
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.last4Digits
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.UUID

/**
 * Writes a finished agent session's output to the database.
 *
 * The session id is the only input: the splitter's is recorded on the file it classified and an extraction's
 * on the classification it was started for (see [AgentSessionStarter]), so each result finds its own target.
 * A session that didn't complete is reported as-is and nothing is written — its status stays readable from
 * the session itself, so there is nothing worth storing.
 *
 * A completed extraction's `result.json` is archived to the classification's model location, the same blob the
 * Azure pipeline writes its model to. That pointer is what the file summaries count as "analyzed", so only a
 * run that completed and saved sets it: a failed or agent-errored session leaves it alone.
 *
 * Saving is a replace, so a second call would drop whatever the first one wrote — including any manual edits
 * made since. A session whose target already holds records is therefore left alone and reported with
 * [AgentSaveResult.alreadySaved] and the ids that are already there, unless the caller passes `override`.
 */
@Singleton
class AgentResultSaver @Inject constructor(
    private val agentSessionLauncher: AgentSessionLauncher,
    private val fileService: FileService,
    private val classificationService: ClassificationService,
    private val statementService: StatementService,
    private val checkService: CheckService,
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}

    fun save(sessionId: String, override: Boolean = false): AgentSaveResult {
        val result = agentSessionLauncher.fetchResult(sessionId)
        if (result.status != AgentSessionResult.Status.COMPLETED) {
            logger.info { "Nothing to save for session $sessionId: ${result.status}${result.error?.let { " ($it)" }.orEmpty()}" }
            return AgentSaveResult(sessionId, result.agent, result.status, error = result.error)
        }

        return when (result.agent) {
            ManagedAgent.SPLITTER -> saveSplitterResult(sessionId, result.output as SplitterOutput, override)
            ManagedAgent.STATEMENT_EXTRACTION ->
                saveStatementExtraction(sessionId, result.output as StatementExtractionOutput, override, result.outputJson)
            ManagedAgent.CHECK_EXTRACTION ->
                saveCheckExtraction(sessionId, result.output as CheckExtractionOutput, override, result.outputJson)
            else -> throw IllegalArgumentException("Session $sessionId belongs to ${result.agent}, whose output isn't saved to the database")
        }
    }

    /**
     * Turns the splitter's page ranges into this file's classifications, each carrying the stamps for its
     * own pages.
     *
     * The bank id is kept as the classification type — [com.goldberg.law.document.model.pdf.DocumentType]
     * resolves an unrecognised one by shape — with the institution's name alongside it. Check pages become
     * their own classification, which the agent extraction runs read their page list from.
     */
    private fun saveSplitterResult(sessionId: String, output: SplitterOutput, override: Boolean): AgentSaveResult {
        val file = fileService.loadFileBySplitterSession(sessionId)

        val existing = classificationService.loadClassifications(file.fileId)
        if (existing.isNotEmpty() && !override) {
            logger.info { "File ${file.fileId} already has ${existing.size} classification(s); not re-saving splitter session $sessionId" }
            return AgentSaveResult(
                sessionId = sessionId,
                agent = ManagedAgent.SPLITTER,
                status = AgentSessionResult.Status.COMPLETED,
                fileId = file.fileId,
                classificationIds = existing.map { it.classificationId },
                alreadySaved = true,
            )
        }

        // Recorded even when the run found nothing to classify, which is when a reviewer most needs it
        fileService.updateSplitterReview(
            file.fileId,
            output.reviewRequired.map { ReviewNote(it.reason, it.pages) },
            output.unassignedPages,
        )

        val stamps = output.bates?.toPageStamps().orEmpty()

        val statementPages = output.boundaries.map { boundary ->
            val pages = (boundary.start..boundary.end).toSet()
            val bank = output.banks[boundary.bankId]
            ClassifiedPages(pages, boundary.bankId, bank?.name, stamps.filterKeys { it in pages }, bank?.source)
        }
        val checkPages = output.checkPages.takeIf { it.isNotEmpty() }?.toSet()
            ?.let { listOf(ClassifiedPages(it, CheckTypes.CHECKS, batesStamps = stamps.filterKeys { page -> page in it })) }
            .orEmpty()

        if (statementPages.isEmpty() && checkPages.isEmpty()) {
            // Nothing to replace the existing classifications with, so leave them (and their records) alone
            logger.warn { "Splitter session $sessionId found no statements or check pages in file ${file.fileId}" }
            return AgentSaveResult(sessionId, ManagedAgent.SPLITTER, AgentSessionResult.Status.COMPLETED, fileId = file.fileId)
        }

        val infos = classificationService.replaceClassifications(ClassifiedFile(file.fileId, statementPages + checkPages))
        logger.info { "Saved ${infos.size} classification(s) for file ${file.fileId} from splitter session $sessionId" }

        return AgentSaveResult(
            sessionId = sessionId,
            agent = ManagedAgent.SPLITTER,
            status = AgentSessionResult.Status.COMPLETED,
            fileId = file.fileId,
            classificationIds = infos.map { it.classificationId },
        )
    }

    /** One statement per account in the range, replacing whatever a previous run left behind. */
    private fun saveStatementExtraction(
        sessionId: String,
        output: StatementExtractionOutput,
        override: Boolean,
        resultJson: String?,
    ): AgentSaveResult {
        val classification = classificationService.loadClassificationByExtractionSession(sessionId)

        val existing = statementService.loadStatementIdsForClassification(classification.classificationId)
        if (existing.isNotEmpty() && !override) {
            logger.info {
                "Classification ${classification.classificationId} already has ${existing.size} statement(s); " +
                    "not re-saving session $sessionId"
            }
            return AgentSaveResult(
                sessionId = sessionId,
                agent = ManagedAgent.STATEMENT_EXTRACTION,
                status = AgentSessionResult.Status.COMPLETED,
                fileId = classification.fileId,
                classificationIds = listOf(classification.classificationId),
                statementIds = existing.toList(),
                alreadySaved = true,
            )
        }

        val statements = output.accounts.map { account ->
            Statement(
                classification = classification,
                statementDetails = account.toStatementDetails(output),
                // Recomputed on load, so there's nothing to store here
                suspiciousReasons = emptyList(),
                transactions = account.toTransactionDetails(),
                dailyBalances = account.dailyBalances.toSortedMap()
                    .mapKeys { (date, _) -> date.toString() }
                    .mapValues { (_, balance) -> balance.asCurrency() },
            )
        }
        val statementIds = statementService.replaceStatements(classification.classificationId, statements)
        archiveResult(classification, resultJson)
        logger.info {
            "Saved ${statementIds.size} statement(s) with ${statements.sumOf { it.transactions.size }} transaction(s) " +
                "for classification ${classification.classificationId} from session $sessionId"
        }

        return AgentSaveResult(
            sessionId = sessionId,
            agent = ManagedAgent.STATEMENT_EXTRACTION,
            status = AgentSessionResult.Status.COMPLETED,
            fileId = classification.fileId,
            classificationIds = listOf(classification.classificationId),
            statementIds = statementIds,
        )
    }

    private fun saveCheckExtraction(
        sessionId: String,
        output: CheckExtractionOutput,
        override: Boolean,
        resultJson: String?,
    ): AgentSaveResult {
        val classification = classificationService.loadClassificationByExtractionSession(sessionId)

        val existing = checkService.loadCheckIdsForClassification(classification.classificationId)
        if (existing.isNotEmpty() && !override) {
            logger.info {
                "Classification ${classification.classificationId} already has ${existing.size} check(s); " +
                    "not re-saving session $sessionId"
            }
            return AgentSaveResult(
                sessionId = sessionId,
                agent = ManagedAgent.CHECK_EXTRACTION,
                status = AgentSessionResult.Status.COMPLETED,
                fileId = classification.fileId,
                classificationIds = listOf(classification.classificationId),
                checkIds = existing.toList(),
                alreadySaved = true,
            )
        }

        // Before the checks, whose existence stops a re-run without override: a failure here leaves nothing behind
        classificationService.updateUnreadablePages(classification.classificationId, output.unreadablePages)
        val checks = output.checks.map { check ->
            CheckDetails(
                checkId = UUID.randomUUID(),
                checkNumber = check.checkNo,
                accountNumber = check.acct?.last4Digits(),
                description = check.memo,
                date = check.date?.toString(),
                amount = check.amt?.asCurrency(),
                to = check.payee,
                // The classification holds the stamps; this column is the Azure pipeline's
                batesStamp = null,
                filePageNumber = check.page,
                reviewStatus = ReviewStatus.initial(check.reviewRequired),
            )
        }
        val checkIds = checkService.replaceChecks(classification, checks)
        archiveResult(classification, resultJson)
        logger.info { "Saved ${checkIds.size} check(s) for classification ${classification.classificationId} from session $sessionId" }

        return AgentSaveResult(
            sessionId = sessionId,
            agent = ManagedAgent.CHECK_EXTRACTION,
            status = AgentSessionResult.Status.COMPLETED,
            fileId = classification.fileId,
            classificationIds = listOf(classification.classificationId),
            checkIds = checkIds,
        )
    }

    /**
     * Archives the agent's `result.json` and points the classification at it. Called only after the records
     * themselves are committed, so the pointer never claims an extraction the database doesn't have — if the
     * archive then fails, the run looks un-analyzed and re-running it overwrites cleanly.
     */
    private fun archiveResult(classification: Classification, resultJson: String?) {
        if (resultJson == null) {
            logger.warn { "No ${ManagedAgent.OUTPUT_FILE} to archive for classification ${classification.classificationId}" }
            return
        }
        val location = dataManager.saveAgentOutput(classification, resultJson)
        classificationService.updateModelLocation(classification.classificationId, location)
        logger.info { "Archived agent result for classification ${classification.classificationId} to $location" }
    }

    /**
     * What the agent reported at statement level — errors, flagged fields and notes — applies to every account on
     * the statement, so each account's statement carries it alongside its own.
     */
    private fun ExtractedAccount.toStatementDetails(output: StatementExtractionOutput): StatementDetails {
        val reviewFields = (output.reviewRequired?.fields.orEmpty() + reviewRequired?.fields.orEmpty())
            .map { it.toStatementProperty() }
            .distinct()
        val reviewNotes = output.reviewRequired?.notes.orEmpty() + reviewRequired?.notes.orEmpty()
        return StatementDetails(
            statementId = UUID.randomUUID(),
            date = output.statementDate?.toString(),
            accountNumber = accountNumber?.last4Digits(),
            beginningBalance = beginningBalance?.asCurrency(),
            endingBalance = endingBalance?.asCurrency(),
            interestCharged = interestCharged?.asCurrency(),
            feesCharged = feesCharged?.asCurrency(),
            // The classification holds the stamps; this column is the Azure pipeline's
            batesStamps = emptyMap(),
            accountName = accountName,
            startDate = output.statementStart?.toString(),
            totalCredits = totalCredits?.asCurrency(),
            totalDebits = totalDebits?.asCurrency(),
            checksTotal = checksTotal?.asCurrency(),
            interestReceived = interestReceived?.asCurrency(),
            txnCountCredit = txnCountCredit,
            txnCountDebit = txnCountDebit,
            txnCount = txnCount,
            summaryArithmeticFields = summaryArithmeticFields.map { statementPropertyOrOtherLine(it) },
            otherCredits = otherCredits.mapValues { (_, amount) -> amount.asCurrency() },
            otherDebits = otherDebits.mapValues { (_, amount) -> amount.asCurrency() },
            agentErrors = (output.errors + errors).distinct(),
            reviewFields = reviewFields,
            reviewNotes = reviewNotes,
            reviewStatus = ReviewStatus.initial(reviewFields.isNotEmpty() || reviewNotes.isNotEmpty()),
        )
    }

    private fun ExtractedAccount.toTransactionDetails(): List<TransactionDetails> {
        val flags = reviewRequired?.transactionFlags(transactions.size).orEmpty()
        return transactions.mapIndexed { index, transaction -> TransactionDetails(
            transactionId = UUID.randomUUID(),
            date = transaction.date?.toString(),
            description = transaction.desc,
            amount = transaction.amt?.asCurrency(),
            checkNumber = transaction.check?.filter { it.isDigit() }?.toIntOrNull(),
            filePageNumber = transaction.page,
            statementIndex = index,
            checkId = null,
            countedIn = transaction.countedIn?.let { statementPropertyOrOtherLine(it) },
            reviewFields = flags[index].orEmpty(),
            reviewStatus = ReviewStatus.initial(index in flags),
        ) }
    }

    /**
     * Turns the agent's per-field lists of transaction indexes into each flagged row's fields, named as
     * [TransactionDetails] properties: the flag belongs to the row, and an index stops pointing at it once a
     * reviewer inserts, deletes or reorders rows.
     */
    private fun AccountReviewRequired.transactionFlags(numTransactions: Int): Map<Int, List<String>> {
        val flags = mutableMapOf<Int, MutableList<String>>()
        mapOf(
            "date" to date,
            "description" to desc,
            "checkNumber" to check,
            "amount" to amt,
        ).forEach { (field, indexes) ->
            indexes.forEach { index ->
                if (index in 0 until numTransactions) flags.getOrPut(index) { mutableListOf() }.add(field)
                else logger.warn { "Agent flagged $field on transaction $index, but the account has only $numTransactions" }
            }
        }
        return flags
    }

    /** The [StatementDetails] property an agent's summary field name refers to. */
    private fun String.toStatementProperty() = STATEMENT_FIELDS[this] ?: snakeToCamelCase()

    /** An other-line label is the agent's own name for that line, not one of our properties, so it's kept as written. */
    private fun ExtractedAccount.statementPropertyOrOtherLine(name: String) =
        if (name in otherCredits || name in otherDebits) name else name.toStatementProperty()

    private fun String.snakeToCamelCase() = split('_').mapIndexed { i, part -> if (i == 0) part else part.replaceFirstChar { it.uppercase() } }.joinToString("")

    companion object {
        /**
         * The agent's field names whose [StatementDetails] property isn't simply their camelCase form. The rest
         * (`beginning_balance` -> `beginningBalance`) convert as they are.
         */
        private val STATEMENT_FIELDS = mapOf("statement_date" to "date", "statement_start" to "startDate")
    }
}

/** What [AgentResultSaver.save] wrote, or why it wrote nothing. */
data class AgentSaveResult(
    val sessionId: String,
    val agent: ManagedAgent?,
    val status: AgentSessionResult.Status,
    val fileId: UUID? = null,
    val classificationIds: List<UUID> = emptyList(),
    val statementIds: List<UUID> = emptyList(),
    val checkIds: List<UUID> = emptyList(),
    /** True when this session's records were already in the database and nothing was written or deleted. */
    val alreadySaved: Boolean = false,
    val error: String? = null,
)
