package com.goldberg.law.function.model.tracking

import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassifiedItem
import com.goldberg.law.entity.InputFile
import com.google.inject.Inject
import com.microsoft.durabletask.TaskOrchestrationContext

/**
 * Initialize with the factory so that we can unit test the status
 */
class OrchestrationStatusFactory @Inject constructor() {

    fun new(
        ctx: TaskOrchestrationContext,
        stage: OrchestrationStage,
        filesToClassify: Set<InputFile>,
        classificationsToAnalyze: Set<Classification>,
        itemsCompleted: Set<ClassifiedItem>
    ): OrchestrationStatus {
        val documentStatusMap = (filesToClassify + classificationsToAnalyze.map { it.inputFile } + itemsCompleted.map { it.classification.inputFile }).toSet().associate {
            it.fileId to DocumentOrchestrationStatus(it.fileId, null, null, null, false)
        }.toMutableMap()
        val orchestrationStatus = OrchestrationStatus(ctx, stage, documentStatusMap)
        (classificationsToAnalyze + itemsCompleted.map { it.classification }.distinct())
            .groupBy { it.inputFile.fileId }
            .forEach { (fileId, classifications) ->
                orchestrationStatus.updateDoc(fileId) {
                    classified = true
                    numStatementPages = classifications.filter { it.documentType.isStatement() }.size
                    numCheckPages = classifications.filter { it.documentType.isCheck() }.size
                }
        }

        itemsCompleted.forEach { cI ->
            orchestrationStatus.updateDoc(cI.classification.fileId) { docsAnalyzed = (docsAnalyzed ?: 0) + 1 }
        }

        return orchestrationStatus.save()
    }
}