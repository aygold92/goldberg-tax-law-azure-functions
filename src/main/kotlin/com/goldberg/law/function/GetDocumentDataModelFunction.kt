package com.goldberg.law.function

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.PutDocumentDataModelRequest
import com.goldberg.law.function.model.request.PutDocumentDataModelResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetDocumentDataModelFunction(
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
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ProcessDataModelActivityInput::class.java)
        val model = dataManager.loadModel(req.clientName, req.pdfPageData)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(PutDocumentDataModelRequest(req.clientName, req.pdfPageData, DocumentDataModelContainer(model)))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error loading model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetDocumentDataModel"
    }
}