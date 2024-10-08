package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.function.model.activity.GetFilesToProcessActivityInput
import com.goldberg.law.function.model.activity.GetFilesToProcessActivityOutput
import com.goldberg.law.util.toStringDetailed
import com.google.common.collect.Sets
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class GetFilesToProcessActivity(private val dataManager: DataManager) {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun getFilesToProcessActivity(@DurableActivityTrigger(name = "input") input: GetFilesToProcessActivityInput, context: ExecutionContext): GetFilesToProcessActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val filesRequested = dataManager.checkFilesExist(input.request.documents)
        val alreadyProcessed = if (!input.request.overwrite) dataManager.getProcessedFiles(input.request.documents)
            .also { logger.info { "[${input.requestId}] Skipping files $it as overwrite is not set" } }
        else setOf()

        return GetFilesToProcessActivityOutput(Sets.difference(filesRequested, alreadyProcessed)).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "GetFilesToProcessActivity"
    }
}