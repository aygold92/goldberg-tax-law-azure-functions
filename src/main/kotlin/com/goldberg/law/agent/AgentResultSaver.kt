package com.goldberg.law.agent

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

        val stamps = output.bates?.toPageStamps().orEmpty()

        val statementPages = output.boundaries.map { boundary ->
            val pages = (boundary.start..boundary.end).toSet()
            ClassifiedPages(pages, boundary.bankId, output.banks[boundary.bankId]?.name, stamps.filterKeys { it in pages })
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

    private fun ExtractedAccount.toStatementDetails(output: StatementExtractionOutput) = StatementDetails(
        statementId = UUID.randomUUID(),
        date = output.statementDate?.toString(),
        accountNumber = accountNumber?.last4Digits(),
        beginningBalance = beginningBalance?.asCurrency(),
        endingBalance = endingBalance?.asCurrency(),
        interestCharged = interestCharged?.asCurrency(),
        feesCharged = feesCharged?.asCurrency(),
        // The classification holds the stamps; this column is the Azure pipeline's
        batesStamps = emptyMap(),
    )

    private fun ExtractedAccount.toTransactionDetails() = transactions.mapIndexed { index, transaction ->
        TransactionDetails(
            transactionId = UUID.randomUUID(),
            date = transaction.date?.toString(),
            description = transaction.desc,
            amount = transaction.amt?.asCurrency(),
            checkNumber = transaction.check?.filter { it.isDigit() }?.toIntOrNull(),
            filePageNumber = transaction.page,
            statementIndex = index,
            checkId = null,
        )
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
