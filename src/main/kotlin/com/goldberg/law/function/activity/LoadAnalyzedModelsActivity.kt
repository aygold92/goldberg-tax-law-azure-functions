package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.LoadAnalyzedModelsActivityInput
import com.goldberg.law.function.model.activity.LoadAnalyzedModelsActivityOutput
import com.goldberg.law.util.mapAsync
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class LoadAnalyzedModelsActivity(private val dataManager: AzureStorageDataManager) {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun loadAnalyzedModels(@DurableActivityTrigger(name = "input") input: LoadAnalyzedModelsActivityInput, context: ExecutionContext): LoadAnalyzedModelsActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val models = input.filenames.mapAsync { filename ->
            val classifications = dataManager.loadDocumentClassification(input.clientName, filename)
            classifications.mapAsync { DocumentDataModelContainer(dataManager.loadModel(input.clientName, it)) }
        }.flatten()


        return LoadAnalyzedModelsActivityOutput(models).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "LoadAnalyzedModels"
    }
}