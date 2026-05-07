package com.goldberg.law.function

import com.goldberg.law.AppModule
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.InputFile
import com.goldberg.law.function.activity.ClassifyDocumentActivity
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.model.ClassifyDocumentActivityInput
import com.goldberg.law.function.activity.model.ClassifyDocumentActivityOutput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityOutput
import com.goldberg.law.function.api.model.ClassificationProcessingOptions
import com.goldberg.law.function.model.tracking.OrchestrationStatus
import com.google.inject.Inject
import com.google.inject.name.Named
import com.microsoft.durabletask.Task
import com.microsoft.durabletask.TaskOrchestrationContext
import java.util.*

class ConcurrentExecutionOrchestrator @Inject constructor(
    @Named(AppModule.NUM_FUNCTION_WORKERS) private val numWorkers: Int,
) {

    fun execClassifyDocuments(
        ctx: TaskOrchestrationContext,
        filesToClassify: List<InputFile>,
        orchestrationStatus: OrchestrationStatus,
    ): Map<UUID, List<Classification>> = execActivityConcurrent(
        ctx,
        filesToClassify,
        ClassifyDocumentActivity.FUNCTION_NAME,
        { inputFile -> ClassifyDocumentActivityInput(ctx.instanceId, inputFile) },
        ClassifyDocumentActivityOutput::class.java,
        { output -> orchestrationStatus.updateDoc(output.fileId) {
            numStatementPages = output.classifications.filter { it.documentType.isStatement() }.size
            numCheckPages = output.classifications.filter { it.documentType.isCheck() }.size
            classified = true
        }.save() }
    ).associate { it.fileId to it.classifications }

    fun execProcessDataModels(
        ctx: TaskOrchestrationContext,
        docsToAnalyze: List<Classification>,
        orchestrationStatus: OrchestrationStatus,
        processingOptions: ClassificationProcessingOptions,
    ): List<ProcessDataModelActivityOutput> = execActivityConcurrent(
        ctx,
        docsToAnalyze,
        ProcessDataModelActivity.FUNCTION_NAME,
        { classification -> ProcessDataModelActivityInput(ctx.instanceId, classification, processingOptions) },
        ProcessDataModelActivityOutput::class.java,
        { (fileId, _) -> orchestrationStatus.updateDoc(fileId) { incrementDocumentsAnalyzed() }.save() }
    )

    fun <I, R> execActivityConcurrent(
        ctx: TaskOrchestrationContext,
        items: List<I>,
        activityName: String,
        activityInput: (I) -> Any,
        output: Class<R>,
        result: (R) -> Unit = { }
    ): List<R> {
        val pendingItems = items.toMutableList()
        val inFlightTasks: MutableList<Task<R>> = mutableListOf()
        val returnItems: MutableList<R> = mutableListOf()
        while(pendingItems.isNotEmpty() || inFlightTasks.isNotEmpty()) {
            while(inFlightTasks.size < numWorkers && pendingItems.isNotEmpty()) {
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