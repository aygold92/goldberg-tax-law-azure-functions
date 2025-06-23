package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.activity.LoadDocumentClassificationsActivityInput
import com.goldberg.law.function.model.activity.LoadDocumentClassificationsActivityOutput
import com.goldberg.law.util.mapAsync
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class LoadDocumentClassificationsActivity(private val dataManager: AzureStorageDataManager) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun loadDocumentClassification(
        @DurableActivityTrigger(name = "input") input: LoadDocumentClassificationsActivityInput,
        context: ExecutionContext
    ): LoadDocumentClassificationsActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val documents = input.filenames.mapAsync {
            it to dataManager.loadDocumentClassification(input.clientName, it)
        }.associate { it.first to it.second }

        return LoadDocumentClassificationsActivityOutput(documents).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "LoadDocumentClassifications"
    }
}