package com.goldberg.law.function.api

import com.goldberg.law.database.service.FileService
import com.goldberg.law.entity.InputFileSummary
import com.goldberg.law.function.api.model.ListInputDocumentsRequest
import com.goldberg.law.util.OBJECT_MAPPER
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
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ListInputDocumentsRequest::class.java)

        // List all input documents for the client
        val inputDocuments: List<InputFileSummary> = fileService.listFiles(req.clientId)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(inputDocuments)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing input documents for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to ex.message))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListInputDocuments"
    }
} 