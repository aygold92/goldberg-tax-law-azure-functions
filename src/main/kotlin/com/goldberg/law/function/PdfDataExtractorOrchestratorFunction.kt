package com.goldberg.law.function

import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.activity.*
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.*
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatusFactory
import com.goldberg.law.util.associate
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.CompositeTaskFailedException
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskFailedException
import com.microsoft.durabletask.TaskOrchestrationContext
import com.microsoft.durabletask.azurefunctions.DurableOrchestrationTrigger
import com.microsoft.durabletask.interruption.OrchestratorBlockedException
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class PdfDataExtractorOrchestratorFunction @Inject constructor(
    private val numWorkers: Int,
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

        /** step 1: ensure the requested files have been uploaded, and return their metadata */
        val orchestrationStatus = orchestrationStatusFactory.new(ctx, OrchestrationStage.VERIFYING_DOCUMENTS, request.documents).save()
        val filesToProcess = ctx.callActivity(
            GetFilesToProcessActivity.FUNCTION_NAME,
            GetFilesToProcessActivityInput(ctx.instanceId, request),
            GetFilesToProcessActivityOutput::class.java
        ).await().filesWithMetadata

        /** step 1.5: determine based on metadata which files need to be split and analyzed (for idempotency) */
        val filesToClassify = filesToProcess.filter { !it.value.classified }
        val alreadyClassifiedDocuments = filesToProcess.filter { it.value.classified }
        val alreadyAnalyzedDocuments = filesToProcess.filter { it.value.analyzed }
        val alreadyClassifiedNotAnalyzedDocuments = alreadyClassifiedDocuments.minus(alreadyAnalyzedDocuments.keys)
        val alreadyAnalyzedNotProcessedToBankStatement = alreadyAnalyzedDocuments.filter { it.value.statements == null }
        val alreadyCompletedDocuments = alreadyAnalyzedDocuments.filter { it.value.statements?.isNotEmpty() == true }

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Classifying files ${filesToClassify.keys}\n" +
                "Already classified files ${alreadyClassifiedDocuments.keys}\n" +
                "Already analyzed files ${alreadyAnalyzedDocuments.keys}" }

        /** step 2: classify the PDFs */
        filesToProcess.forEach { (filename, metadata) -> orchestrationStatus.updateDoc(filename) {
            numStatements = metadata.numstatements
            statementsCompleted = if (metadata.analyzed) metadata.numstatements else 0
            classified = metadata.classified
        } }
        orchestrationStatus.updateStage(OrchestrationStage.CLASSIFYING_DOCUMENTS).save()

        val newDocumentClassifications = concurrentExecutionOrchestrator.execClassifyDocuments(
            ctx, filesToClassify.keys.toList(), orchestrationStatus, numWorkers, request.clientName
        )

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Successfully classified pdfs: $filesToClassify" }

        /** step 2.5: load the already classified but not analyzed docs */
        val docsAlreadyClassifiedNotAnalyzed = if (alreadyClassifiedNotAnalyzedDocuments.isNotEmpty()) {
            ctx.callActivity(
                LoadDocumentClassificationsActivity.FUNCTION_NAME,
                LoadDocumentClassificationsActivityInput(ctx.instanceId, request.clientName, alreadyClassifiedNotAnalyzedDocuments.keys),
                LoadDocumentClassificationsActivityOutput::class.java
            ).await().classifiedDocumentsMap
        } else mapOf()

        /** step 3: the heavy lifting: extract the data from the necessary documents.  Break it into groups for throttling purposes */
        orchestrationStatus.updateStage(OrchestrationStage.EXTRACTING_DATA).save()

        val updateMetadataTasks: MutableList<Task<Void>> = mutableListOf()
        val docsToAnalyze = (newDocumentClassifications + docsAlreadyClassifiedNotAnalyzed).values.flatten()

        val analyzedModels = concurrentExecutionOrchestrator.execProcessDataModels(
            ctx, docsToAnalyze, orchestrationStatus, numWorkers, request.clientName, updateMetadataTasks
        )

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully processed all models" }

        ctx.allOf(updateMetadataTasks).await()
        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully updated all input file metadata" }

        /** step 3.5: load models for any files that have already */
        orchestrationStatus.updateStage(OrchestrationStage.CREATING_BANK_STATEMENTS).save()
        if (alreadyAnalyzedNotProcessedToBankStatement.isNotEmpty()) {
            analyzedModels.addAll(ctx.callActivity(
                LoadAnalyzedModelsActivity.FUNCTION_NAME,
                LoadAnalyzedModelsActivityInput(ctx.instanceId, request.clientName, alreadyAnalyzedNotProcessedToBankStatement.keys),
                LoadAnalyzedModelsActivityOutput::class.java
            ).await().models)
        }

        /** step 4: create bank statements using all the analyzed models */
        // TODO: do I need to do this? or wil everything happen in ProcessDataModels now?
        // TODO: In cases that require extra post processing (joint statements, NFCU), maybe I should do this here?  SO then I should do everything here?
        val finalResult = ctx.callActivity(
            ProcessStatementsActivity.FUNCTION_NAME,
            ProcessStatementsActivityInput(ctx.instanceId, request.clientName, analyzedModels, orchestrationStatus.documentMetadataMap().minus(alreadyCompletedDocuments.keys)),
            ProcessStatementsActivityOutput::class.java
        ).await()
            .also { logger.info { "[${ctx.instanceId}] Created new bank statements: ${it.filenameStatementMap}" } }

        // TODO: should probably handle the weird case where metadata is incorrect (and statements is null on an already analyzed document)
        AnalyzeDocumentResult.success(finalResult.filenameStatementMap + alreadyCompletedDocuments.associate { filename, metadata -> filename to metadata.statements!! })
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