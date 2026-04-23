package com.goldberg.law.function.api

import com.goldberg.law.database.service.FileService
import com.goldberg.law.function.api.model.ApiResult
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListInputDocumentsFunction @Inject constructor(
    private val fileService: FileService,
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
        logger.info { "[${ctx.invocationId}] listing input documents for clientId=$clientId" }

        val inputDocuments = fileService.listFileSummaries(clientId)

        request.createResponseBuilder(HttpStatus.OK)
            .body(inputDocuments)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing input documents for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListInputDocuments"
    }
}
