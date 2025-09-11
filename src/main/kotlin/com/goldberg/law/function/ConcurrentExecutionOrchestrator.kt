package com.goldberg.law.function

import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.activity.ClassifyDocumentActivity
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.UpdateMetadataActivity
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityInput
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityOutput
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.function.model.activity.UpdateMetadataActivityInput
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext

class ConcurrentExecutionOrchestrator {

    fun execClassifyDocuments(
        ctx: TaskOrchestrationContext,
        filesToClassify: List<String>,
        orchestrationStatus: OrchestrationStatus,
        numWorkers: Int,
        clientName: String
    ): Map<String, List<ClassifiedPdfMetadata>> = execActivityConcurrent(
        ctx,
        filesToClassify,
        numWorkers,
        ClassifyDocumentActivity.FUNCTION_NAME,
        { filename -> ClassifyDocumentActivityInput(ctx.instanceId, clientName, filename) },
        ClassifyDocumentActivityOutput::class.java,
        { output -> orchestrationStatus.updateDoc(output.filename) { numStatements = output.classifiedDocuments.size; classified = true }.save() }
    ).associate { it.filename to it.classifiedDocuments }

    fun execProcessDataModels(
        ctx: TaskOrchestrationContext,
        docsToAnalyze: List<ClassifiedPdfMetadata>,
        orchestrationStatus: OrchestrationStatus,
        numWorkers: Int,
        clientName: String,
        updateMetadataTasks: MutableList<Task<Void>>
    ): MutableSet<DocumentDataModelContainer> = execActivityConcurrent(
        ctx,
        docsToAnalyze,
        numWorkers,
        ProcessDataModelActivity.FUNCTION_NAME,
        { doc -> ProcessDataModelActivityInput(ctx.instanceId, clientName, doc) },
        DocumentDataModelContainer::class.java,
        { dataModelContainer ->
            val completedDocFilename = dataModelContainer.getDocumentDataModel().pageMetadata.filename
            orchestrationStatus.updateDoc(completedDocFilename) { incrementStatementsCompleted() }
                .save()

            if (orchestrationStatus.docIsComplete(completedDocFilename)) {
                val metadata = InputFileMetadata(orchestrationStatus.getNumStatementsForDoc(completedDocFilename), true, true)
                updateMetadataTasks.add(
                    ctx.callActivity(UpdateMetadataActivity.FUNCTION_NAME, UpdateMetadataActivityInput(clientName, completedDocFilename, metadata))
                )
            }
        }
    ).toMutableSet()

    fun <I, R> execActivityConcurrent(
        ctx: TaskOrchestrationContext,
        items: List<I>, concurrentTasks: Int,
        activityName: String, activityInput: (I) -> Any,
        output: Class<R>,
        result: (R) -> Unit = { }
    ): List<R> {
        val pendingItems = items.toMutableList()
        val inFlightTasks: MutableList<Task<R>> = mutableListOf()
        val returnItems: MutableList<R> = mutableListOf()
        while(pendingItems.isNotEmpty() || inFlightTasks.isNotEmpty()) {
            while(inFlightTasks.size < concurrentTasks && pendingItems.isNotEmpty()) {
                inFlightTasks.add(ctx.callActivity(activityName, activityInput(pendingItems.removeFirst()), output))
            }

            val completedTask = ctx.anyOf(inFlightTasks as List<Task<*>>).await()
            val completedObj = completedTask.await() as R
            result(completedObj)
            returnItems.add(completedObj)

            inFlightTasks.remove(completedTask)
        }

        return returnItems
    }
}