package com.goldberg.law.function

import com.goldberg.law.function.activity.*
import com.goldberg.law.function.model.*
import com.goldberg.law.function.model.activity.*
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.function.model.tracking.DocumentOrchestrationStatus
import com.goldberg.law.function.model.tracking.OrchestrationStage
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.goldberg.law.util.breakIntoGroups
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

class PdfDataExtractorOrchestratorFunction @Inject constructor(private val numWorkers: Int) {
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

        ctx.setCustomStatus(OrchestrationStatus.ofAction(OrchestrationStage.VERIFYING_DOCUMENTS, request.documents))
        // step 1: ensure the requested files have been uploaded, and return their metadata
        val filesToProcess = ctx.callActivity(
            GetFilesToProcessActivity.FUNCTION_NAME,
            GetFilesToProcessActivityInput(ctx.instanceId, request),
            GetFilesToProcessActivityOutput::class.java
        ).await().filesWithMetadata

        // step 2: determine based on metadata which files need to be split and analyzed (for idempotency)
        val filesToSplit = filesToProcess.filter { !it.metadata.split }
        val alreadySplitDocuments = filesToProcess.filter { it.metadata.split }.toSet()
        val alreadyAnalyzedDocuments = filesToProcess.filter { it.metadata.analyzed }.toSet()
        val alreadyAnalyzedNotProcessedToBankStatement = alreadyAnalyzedDocuments.filter { it.metadata.statements == null }
        val alreadySplitNotAnalyzedDocuments = alreadySplitDocuments - alreadyAnalyzedDocuments

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Splitting files ${filesToSplit.map { it.name }}\n" +
                "Already split files ${alreadySplitDocuments.map { it.name }}\n" +
                "Already analyzed files ${alreadyAnalyzedDocuments.map { it.name }}" }

        // step 3: split the necessary PDFs
        val splitDocumentTasks = filesToSplit.map { file ->
            ctx.callActivity(
                SplitPdfActivity.FUNCTION_NAME,
                SplitPdfActivityInput(ctx.instanceId, file.name, request.overwrite),
                SplitPdfActivityOutput::class.java
            )
        }
        val allTasks: Task<List<SplitPdfActivityOutput>> = ctx.allOf(splitDocumentTasks)
        val newlySplitDocumentsResults: List<SplitPdfActivityOutput> = allTasks.await()
        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Successfully split pdfs: $filesToSplit" }

        val pagesAlreadySplitNotAnalyzed: Set<PdfPageData> = alreadySplitNotAnalyzedDocuments.flatMap { pdfDocument ->
            (1.. pdfDocument.metadata.totalpages!!).toList().map { PdfPageData(pdfDocument.name, it) }
        }.toSet()

        val pagesToAnalyze: Set<PdfPageData> = pagesAlreadySplitNotAnalyzed + newlySplitDocumentsResults.flatMap { it.pdfPages }

        // set status for all documents. The ones that have already been analyzed are already completed.
        var documentsStatus: List<DocumentOrchestrationStatus> =
            newlySplitDocumentsResults.map { DocumentOrchestrationStatus(it.pdfPages.first().fileName, it.pdfPages.size, 0, InputFileMetadata(true, it.pdfPages.size)) } +
                alreadySplitNotAnalyzedDocuments.map { DocumentOrchestrationStatus(it.name, it.metadata.totalpages, 0, it.metadata) } +
                alreadyAnalyzedDocuments.map { DocumentOrchestrationStatus(it.name, it.metadata.totalpages, it.metadata.totalpages, it.metadata) }

        val dataModels = mutableSetOf<DocumentDataModelContainer>()
        val updateMetadataTasks: MutableList<Task<Void>> = mutableListOf()
        var documentsCompleted = 0
        val totalDocumentsToAnalyze = pagesToAnalyze.size
        ctx.setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, documentsStatus, totalDocumentsToAnalyze, documentsCompleted))
        // step 4: the heavy lifting: extract the data from the necessary documents.  Break it into groups for throttling purposes
        val documentGroupsToAnalyze = pagesToAnalyze.breakIntoGroups(pagesToAnalyze.size / numWorkers)
        documentGroupsToAnalyze.forEach { documentGroup ->
            val documentDataModelTasks = documentGroup.value.map { pdfPage ->
                ctx.callActivity(
                    ProcessDataModelActivity.FUNCTION_NAME,
                    ProcessDataModelActivityInput(ctx.instanceId, pdfPage, request.findClassifiedTypeOverride(pdfPage)),
                    DocumentDataModelContainer::class.java
                )
            }
            dataModels.addAll(ctx.allOf(documentDataModelTasks).await())

            documentsCompleted += documentGroup.value.size

            val processedDocuments = documentGroup.value.groupBy { it.fileName }

            documentsStatus = documentsStatus.map {
                val newPagesCompleted = it.pagesCompleted!! + (processedDocuments[it.fileName]?.size ?: 0)
                // Step 4.1: update the metadata when analysis is complete
                if (newPagesCompleted == it.totalPages && !it.metadata!!.analyzed) {
                    val newMetadata = it.metadata.copy(analyzed = true)
                    updateMetadataTasks.add(ctx.callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(it.fileName, newMetadata)))
                    it.copy(pagesCompleted = newPagesCompleted, metadata = newMetadata)
                } else {
                    it.copy(pagesCompleted = newPagesCompleted)
                }
            }

            ctx.setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, documentsStatus, totalDocumentsToAnalyze, documentsCompleted))

            if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Processed group ${documentGroup.key + 1}, now completed $documentsCompleted docs" }
        }

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully processed all models" }
        ctx.allOf(updateMetadataTasks).await()
        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully updated all input file metadata" }

        if (alreadyAnalyzedNotProcessedToBankStatement.isNotEmpty()) {
            val pagesAlreadyAnalyzedNotInBankStatement: Set<PdfPageData> = alreadyAnalyzedNotProcessedToBankStatement.flatMap { pdfDocument ->
                (1.. pdfDocument.metadata.totalpages!!).toList().map { PdfPageData(pdfDocument.name, it) }
            }.toSet()
            val alreadyAnalyzedModelsNotInBankStatement = ctx.callActivity(
                LoadAnalyzedModelsActivity.FUNCTION_NAME,
                LoadAnalyzedModelsActivityInput(ctx.instanceId, pagesAlreadyAnalyzedNotInBankStatement),
                LoadAnalyzedModelsActivityOutput::class.java
            ).await().models
            dataModels.addAll(alreadyAnalyzedModelsNotInBankStatement)
        }

        // step 5: create bank statements using all the analyzed models
        // TODO: if overwrite feature is on, should pass all data models
        val finalResult = ctx.callActivity(
            ProcessStatementsActivity.FUNCTION_NAME,
            ProcessStatementsActivityInput(ctx.instanceId, dataModels, documentsStatus.associateBy({ it.fileName }, { it.metadata!! })),
            ProcessStatementsActivityOutput::class.java
        ).await()
            .also { logger.info { "[${ctx.instanceId}] Got Final Result: ${it.fileNames}" } }

        // TODO: should probably handle this weird case where metadata is incorrect (and statements is null on an already analyzed document)
        AnalyzeDocumentResult.success(finalResult.fileNames + alreadyAnalyzedDocuments.flatMap { it.metadata.statements ?: setOf() })
    } catch (ex: Throwable) {
        if (ex is OrchestratorBlockedException) {
            throw ex
        }

        logger.info(ex) { "[${ctx.instanceId}] Caught unexpected exception" }
        // TODO: create/categorize customer facing exceptions, etc.
        if (ex is TaskFailedException || ex is CompositeTaskFailedException) {
            AnalyzeDocumentResult.failed(ex.cause ?: ex)
        } else {
            AnalyzeDocumentResult.failed(ex)
        }
    }

    companion object {
        const val FUNCTION_NAME = "PdfDataExtractorOrchestratorFunction"
    }
}