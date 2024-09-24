package com.goldberg.law.function.activity

import com.goldberg.law.PdfExtractorMain
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.activity.ProcessStatementsActivityOutput
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class ProcessStatementsAndChecksActivity @Inject constructor(
    private val pdfExtractorMain: PdfExtractorMain
) {
    private val logger = KotlinLogging.logger {}

    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processStatementsAndChecksActivity(
        @DurableActivityTrigger(name = "input") input: ProcessStatementsActivityInput,
        context: ExecutionContext
    ): ProcessStatementsActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.documentDataModels.size} models" }
        return ProcessStatementsActivityOutput(pdfExtractorMain.processStatementsAndChecks(
            input.request.outputDirectory,
            input.request.outputFile ?: input.requestId,
            input.documentDataModels.mapNotNull { it.statementDataModel },
            input.documentDataModels.mapNotNull { it.checkDataModel }
        ))
//        return ProcessStatementsAndChecksActivityOutput(listOf("Test", "test"))
    }

    companion object {
        const val FUNCTION_NAME = "ProcessStatementsAndChecksActivity"
    }
}