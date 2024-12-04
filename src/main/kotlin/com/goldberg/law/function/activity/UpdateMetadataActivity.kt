package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.function.model.activity.UpdateMetadataActivityInput
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class UpdateMetadataActivity @Inject constructor(private val dataManager: DataManager){
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun updateMetadata(@DurableActivityTrigger(name = "input") input: UpdateMetadataActivityInput, context: ExecutionContext) {
        logger.info { "[${context.invocationId}] processing $input" }
        dataManager.updateInputPdfMetadata(input.filename, input.metadata)
        logger.info { "[${context.invocationId}] successfully applied ${input.metadata} to ${input.filename}" }
    }

    companion object {
        const val FUNCTION_NAME = "UpdateMetadata"
    }

}