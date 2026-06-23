package com.goldberg.law.function.api

import com.goldberg.law.database.service.CheckService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.DeleteCheckRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DeleteCheckFunction @Inject constructor(
    private val checkService: CheckService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), DeleteCheckRequest::class.java)

        checkService.deleteCheck(req.checkId)

        logger.info { "Deleted check ${req.checkId}" }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ApiResult(ApiResult.ApiStatus.Success))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error deleting check for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "DeleteCheck"
    }
}
