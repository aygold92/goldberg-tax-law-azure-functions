package com.goldberg.law.function

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.LoadTransactionsFromModelRequest
import com.goldberg.law.function.model.request.PutDocumentDataModelRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class LoadTransactionsFromModelFunction(
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), LoadTransactionsFromModelRequest::class.java)
        val model = dataManager.loadModel(req.clientName, req.pdfPageData)
        if (model is StatementDataModel) {
            request!!.createResponseBuilder(HttpStatus.OK)
                .body(model.getTransactionRecords(fromWrittenDate(req.statementDate), TransactionHistoryPageMetadata(req.pdfPageData.page)))
                .build()
        } else {
            val type = if (model.isCheck()) "check" else "extra pages"
            throw RuntimeException("Model ${req.clientName}/${req.pdfPageData} was classified as type '$type', please re-analyze the page first")
        }
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error loading model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "LoadTransactionsFromModel"
    }
}