package com.goldberg.law.function

import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import com.microsoft.durabletask.azurefunctions.DurableClientContext
import com.microsoft.durabletask.azurefunctions.DurableClientInput
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class InitAnalyzeDocumentsFunction {
    private val logger = KotlinLogging.logger {}

    /**
     * This HTTP-triggered function starts the orchestration.
     */
    @FunctionName(FUNCTION_NAME)
    fun initAnalyzeDocumentsFunction(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        @DurableClientInput(name = "durableContext") durableContext: DurableClientContext,
        context: ExecutionContext
    ): HttpResponseMessage {

        logger.info {"Processing document upload [${context.invocationId}]" }
        val analyzeDocumentRequest = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), AzureAnalyzeDocumentsRequest::class.java)

        val instanceId = durableContext.client.scheduleNewOrchestrationInstance(PdfDataExtractorOrchestratorFunction.FUNCTION_NAME, analyzeDocumentRequest)
        logger.info {"Processing document request [${instanceId}] with request [${analyzeDocumentRequest.toStringDetailed()}]" }
        return durableContext.createCheckStatusResponse(request, instanceId)
    }

    companion object {
        const val FUNCTION_NAME = "InitAnalyzeDocuments"
    }
}