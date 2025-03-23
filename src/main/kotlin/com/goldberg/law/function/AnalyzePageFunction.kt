package com.goldberg.law.function

import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AnalyzePagesRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class AnalyzePageFunction(
    private val processDataModelActivity: ProcessDataModelActivity,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), AnalyzePagesRequest::class.java)

        val models = req.pageRequests.mapAsync {
            processDataModelActivity.processDataModel(it.copy(requestId = "${it.requestId} - ${it.pdfPageData.nameWithPage()}"), ctx)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(models)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error analyzing for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "AnalyzePage"
    }
}