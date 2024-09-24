package com.goldberg.law.function

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.SASTokenRequest
import com.goldberg.law.function.model.request.SASTokenResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*
import javax.inject.Inject

class RequestSASTokenFunction @Inject constructor(
    private val dataManager: DataManager,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.toStringDetailed()}" }
        val req = OBJECT_MAPPER.readValue(request.body.orElseThrow(), SASTokenRequest::class.java)

        

        request.createResponseBuilder(HttpStatus.OK)
            .body(SASTokenResponse(""))
            .build()
    } catch (ex: Exception) {
        // TODO: fix this
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }
    companion object {
        const val FUNCTION_NAME = "RequestSASToken"
    } {
}