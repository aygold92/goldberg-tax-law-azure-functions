package com.goldberg.law.function.activity

import com.goldberg.law.database.service.FileService
import com.goldberg.law.function.activity.model.GetFilesToProcessActivityInput
import com.goldberg.law.function.activity.model.GetFilesToProcessActivityOutput
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Inject
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class GetFilesToProcessActivity @Inject constructor(private val fileService: FileService) {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun getFilesToProcess(@DurableActivityTrigger(name = "input") input: GetFilesToProcessActivityInput, context: ExecutionContext): GetFilesToProcessActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }


        return fileService.loadFiles(input.fileIds).let { (inputFiles, classifications, classifiedItems) ->
            GetFilesToProcessActivityOutput(inputFiles, classifications, classifiedItems).also {
                logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
            }
        }
    }

    companion object {
        const val FUNCTION_NAME = "GetFilesToProcessActivity"
    }
}