package com.goldberg.law.function.api

import com.goldberg.law.database.service.CheckService
import com.goldberg.law.function.api.model.ApiResult
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListChecksFunction @Inject constructor(
    private val checkService: CheckService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val clientId = UUID.fromString(request!!.queryParameters["clientId"]
            ?: throw IllegalArgumentException("Missing required query parameter: clientId"))
        logger.info { "[${ctx.invocationId}] listing checks for clientId=$clientId" }

        val checks = checkService.listChecks(clientId)

        request.createResponseBuilder(HttpStatus.OK)
            .body(checks)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing checks for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListChecks"
    }
}
