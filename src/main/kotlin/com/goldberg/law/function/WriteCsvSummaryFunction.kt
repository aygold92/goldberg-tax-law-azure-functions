package com.goldberg.law.function

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.AccountSummaryCreator
import com.goldberg.law.document.CsvCreator
import com.goldberg.law.document.getChecksUsed
import com.goldberg.law.document.getMissingChecks
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.WriteCsvSummaryRequest
import com.goldberg.law.function.model.request.WriteCsvSummaryResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.getDocumentName
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import javax.inject.Inject

class WriteCsvSummaryFunction @Inject constructor(
    private val dataManager: AzureStorageDataManager,
    private val accountSummaryCreator: AccountSummaryCreator,
    private val csvCreator: CsvCreator
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.body.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request.body.orElseThrow(), WriteCsvSummaryRequest::class.java)

        val statements = req.statementKeys.mapAsync { dataManager.loadBankStatement(req.clientName, it.getDocumentName()) }
        val summary = accountSummaryCreator.createSummary(statements).also { logger.debug { it } }

        val outputFile = req.outputFile ?: ctx.invocationId
        val filePrefix = listOfNotNull(req.outputDirectory, outputFile.getDocumentName(), outputFile.getDocumentName()).joinToString("/")

        val checkSummary = csvCreator.checkSummaryToCsv(statements.getChecksUsed(), statements.getMissingChecks(), setOf())
        val accountSummary = csvCreator.accountSummaryToCsv(summary)
        val statementSummary = csvCreator.statementSummaryToCsv(statements.map { it.toStatementSummary() })
        val records = csvCreator.recordsToCsv(statements)

        request.createResponseBuilder(HttpStatus.OK)
            .body(WriteCsvSummaryResponse(
                AnalyzeDocumentResult.Status.SUCCESS,
                    checkSummaryFile = dataManager.saveCsvOutput(req.clientName, "${filePrefix}_CheckSummary.csv", checkSummary),
                    accountSummaryFile = dataManager.saveCsvOutput(req.clientName, "${filePrefix}_AccountSummary.csv", accountSummary),
                    statementSummaryFile = dataManager.saveCsvOutput(req.clientName, "${filePrefix}_StatementSummary.csv", statementSummary),
                    recordsFile = dataManager.saveCsvOutput(req.clientName, "${filePrefix}_Records.csv", records),
                )
            )
            .build()
    } catch (ex: Exception) {
        // TODO: fix this
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(WriteCsvSummaryResponse.failed(ex))
            .build()
    }
    companion object {
        const val FUNCTION_NAME = "WriteCsvSummary"
    }
}