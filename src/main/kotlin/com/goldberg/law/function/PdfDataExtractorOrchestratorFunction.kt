package com.goldberg.law.function

import com.goldberg.law.function.activity.GetFilesToProcessActivity
import com.goldberg.law.function.activity.MatchChecksToStatementsActivity
import com.goldberg.law.function.activity.model.GetFilesToProcessActivityInput
import com.goldberg.law.function.activity.model.GetFilesToProcessActivityOutput
import com.goldberg.law.function.activity.model.MatchChecksToStatementsActivityInput
import com.goldberg.law.function.activity.model.MatchChecksToStatementsActivityOutput
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatusFactory
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.CompositeTaskFailedException
import com.microsoft.durabletask.TaskFailedException
import com.microsoft.durabletask.TaskOrchestrationContext
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger
import com.microsoft.durabletask.interruption.OrchestratorBlockedException
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class PdfDataExtractorOrchestratorFunction @Inject constructor(
    private val concurrentExecutionOrchestrator: ConcurrentExecutionOrchestrator,
    private val orchestrationStatusFactory: OrchestrationStatusFactory,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * This is the orchestrator function, which can schedule activity functions, create durable timers,
     * or wait for external events in a way that's completely fault-tolerant.
     */
    @FunctionName(FUNCTION_NAME)
    fun pdfDataExtractorOrchestrator(
        @DurableOrchestrationTrigger(name = "taskOrchestrationContext") ctx: TaskOrchestrationContext
    ): AnalyzeDocumentResult = try {
        val request = ctx.getInput(AzureAnalyzeDocumentsRequest::class.java)

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] processing ${request.toStringDetailed()}" }

        /** step 1: ensure the requested files have been uploaded, and return the stored info */
        val (filesToClassify, classificationsToAnalyze, itemsCompleted) = ctx.callActivity(
            GetFilesToProcessActivity.FUNCTION_NAME,
            GetFilesToProcessActivityInput(ctx.instanceId, request.fileIds),
            GetFilesToProcessActivityOutput::class.java
        ).await()

        val orchestrationStatus = orchestrationStatusFactory.new(ctx, OrchestrationStage.CLASSIFYING_DOCUMENTS, filesToClassify, classificationsToAnalyze, itemsCompleted)

        /** step 2: classify the PDFs */
        val newDocumentClassifications = concurrentExecutionOrchestrator.execClassifyDocuments(
            ctx,
            filesToClassify.toList(),
            orchestrationStatus
        )

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Successfully classified pdfs: $filesToClassify" }

        /** step 3: the heavy lifting: extract the data from the necessary documents.  Break it into groups for throttling purposes */
        orchestrationStatus.updateStage(OrchestrationStage.EXTRACTING_DATA).save()

        val processDataModelActivityOutputs = concurrentExecutionOrchestrator.execProcessDataModels(
            ctx,
            newDocumentClassifications.values.flatten(),
            orchestrationStatus
        )
        val documentsByFile = processDataModelActivityOutputs.groupBy({ it.fileId }, { it.extractedDocumentIds })
            .map { (fileId, extractedDocuments) -> fileId to ExtractedDocumentIds(
                statementIds = extractedDocuments.flatMap { it.statementIds }.toSet(),
                checkIds = extractedDocuments.flatMap { it.checkIds }.toSet(),
            )}.toMap()

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully processed all models" }

        /** step 4: if there are any checks, try to match them */
        orchestrationStatus.updateStage(OrchestrationStage.MATCHING_CHECKS).save()
        ctx.callActivity(
            MatchChecksToStatementsActivity.FUNCTION_NAME,
            MatchChecksToStatementsActivityInput(ctx.instanceId, request.clientId, documentsByFile),
            MatchChecksToStatementsActivityOutput::class.java
        ).await()

        AnalyzeDocumentResult.success(documentsByFile)
    } catch (ex: Throwable) {
        if (ex is OrchestratorBlockedException) {
            throw ex
        }

        logger.info(ex) { "[${ctx.instanceId}] Caught unexpected exception" }
        // TODO: create/categorize customer facing exceptions, etc.
        if (ex is TaskFailedException) {
            AnalyzeDocumentResult.failed(ex)
        } else if (ex is CompositeTaskFailedException) {
            AnalyzeDocumentResult.failed(ex.exceptions)
        } else {
            AnalyzeDocumentResult.failed(ex)
        }
    }.also { logger.info { "[${ctx.instanceId}] returning final result ${it.toStringDetailed()}" } }

    companion object {
        const val FUNCTION_NAME = "PdfDataExtractorOrchestratorFunction"
    }
}