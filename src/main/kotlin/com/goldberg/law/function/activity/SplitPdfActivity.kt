package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.function.model.activity.SplitPdfActivityInput
import com.goldberg.law.function.model.activity.SplitPdfActivityOutput
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class SplitPdfActivity @Inject constructor(
    private val dataManager: DataManager,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun splitPdfActivity(@DurableActivityTrigger(name = "name") input: SplitPdfActivityInput, context: ExecutionContext): SplitPdfActivityOutput = try {
        logger.info { "[${input.requestId}][${context.invocationId}] processing $input" }
        val pages = dataManager.loadInputPdfDocumentPages(input.fileName)
            .onEach {
                dataManager.savePdfPage(it, input.overwrite)
            }
        pages.mapAsync { page -> dataManager.savePdfPage(page, input.overwrite) }
        SplitPdfActivityOutput(pages.map { it.pdfPage() }.toSet())
            .also { logger.info { "[${input.requestId}][${context.invocationId}] returning $it" } }
    } catch (ex: Throwable) {
        logger.error(ex) { "[${input.requestId}] Unexpected Exception when processing ${context.functionName}/${context.invocationId}" }
        throw ex
    }

    companion object {
        const val FUNCTION_NAME = "SplitPdfDocumentActivity"
    }
}