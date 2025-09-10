package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.activity.GetFilesToProcessActivityInput
import com.goldberg.law.function.model.activity.GetFilesToProcessActivityOutput
import com.goldberg.law.util.toStringDetailed
import com.google.common.collect.Sets
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class GetFilesToProcessActivity(private val dataManager: AzureStorageDataManager) {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun getFilesToProcess(@DurableActivityTrigger(name = "input") input: GetFilesToProcessActivityInput, context: ExecutionContext): GetFilesToProcessActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val allFiles = dataManager.listInputPdfDocuments(input.request.clientName)
        val requestedFileNames = input.request.documents
        val existingFiles = allFiles.keys
        val intersection = existingFiles.intersect(requestedFileNames)
        if (!intersection.containsAll(requestedFileNames)) {
            throw IllegalArgumentException("The following files do not exist: ${requestedFileNames.subtract(intersection)}")
        }
        val filesRequested = allFiles.filter { intersection.contains(it.key) }
        return GetFilesToProcessActivityOutput(filesRequested).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "GetFilesToProcessActivity"
    }
}