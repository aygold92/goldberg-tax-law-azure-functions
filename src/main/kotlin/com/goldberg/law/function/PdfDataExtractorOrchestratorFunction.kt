package com.goldberg.law.function

import com.goldberg.law.function.activity.*
import com.goldberg.law.function.model.*
import com.goldberg.law.function.model.activity.*
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.util.breakIntoGroups
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.CompositeTaskFailedException
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
        val relevantFiles = ctx.callActivity(
            GetFilesToProcessActivity.FUNCTION_NAME,
            GetFilesToProcessActivityInput(ctx.instanceId, request),
            GetFilesToProcessActivityOutput::class.java
        ).await().relevantFiles

        val splitDocumentTasks = relevantFiles.map { relevantFile ->
            ctx.callActivity(
                SplitPdfActivity.FUNCTION_NAME,
                SplitPdfActivityInput(ctx.instanceId, relevantFile, request.overwrite),
                SplitPdfActivityOutput::class.java
            )
        }

        val inputDocumentsByFile = ctx.allOf(splitDocumentTasks).await()
        val inputDocuments = inputDocumentsByFile.flatMap { it.pdfPages }

        var documentsStatus = inputDocumentsByFile.map { DocumentOrchestrationStatus(it.pdfPages.first().name, it.pdfPages.size, 0) }

        if (!ctx.isReplaying) logger.info { "Successfully split pdfs" }

        val dataModels = mutableListOf<DocumentDataModelContainer>()

        val groupedDocuments = inputDocuments.breakIntoGroups(inputDocuments.size / numWorkers)
        var documentsCompleted = 0
        val totalDocuments = inputDocuments.size
        ctx.setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, documentsStatus, totalDocuments, documentsCompleted))
        groupedDocuments.forEach { documentGroup ->
            val documentDataModelTasks = documentGroup.value.map { pdfPage ->
                ctx.callActivity(
                    ProcessDataModelActivity.FUNCTION_NAME,
                    ProcessDataModelActivityInput(ctx.instanceId, pdfPage, request.findClassifiedTypeOverride(pdfPage)),
                    DocumentDataModelContainer::class.java
                )
            }
            dataModels.addAll(ctx.allOf(documentDataModelTasks).await())

            documentsCompleted += documentGroup.value.size

            val processedDocuments = documentGroup.value.groupBy { it.name }
            documentsStatus = documentsStatus.map { it.copy(pagesCompleted = it.pagesCompleted!! + (processedDocuments[it.documentName]?.size ?: 0)) }
            ctx.setCustomStatus(OrchestrationStatus(OrchestrationStage.EXTRACTING_DATA, documentsStatus, totalDocuments, documentsCompleted))
            if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] Processed group ${documentGroup.key + 1}, now completed $documentsCompleted docs" }
        }

        if (!ctx.isReplaying) logger.info { "[${ctx.instanceId}] successfully processed all models" }

        val finalResult = ctx.callActivity(
            ProcessStatementsActivity.FUNCTION_NAME,
            ProcessStatementsActivityInput(ctx.instanceId, request, dataModels),
            ProcessStatementsActivityOutput::class.java
        ).await()
            .also { logger.info { "[${ctx.instanceId}] Got Final Result: ${it.fileNames}" } }

        AnalyzeDocumentResult.success(finalResult.fileNames)
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