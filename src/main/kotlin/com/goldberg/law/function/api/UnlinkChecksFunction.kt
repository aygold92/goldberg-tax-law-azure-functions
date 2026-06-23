package com.goldberg.law.function.api

import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.UnlinkChecksRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class UnlinkChecksFunction @Inject constructor(
    private val transactionService: TransactionService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), UnlinkChecksRequest::class.java)

        transactionService.unlinkChecks(req.checkIds)

        logger.info { "Unlinked ${req.checkIds.size} check(s)" }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ApiResult(ApiResult.ApiStatus.Success))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error unlinking checks for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "UnlinkChecks"
    }
}
