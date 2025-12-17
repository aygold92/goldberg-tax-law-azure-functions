package com.goldberg.law.function.api

import com.goldberg.law.database.service.FileService
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.GetInputFileSummaryRequest
import com.goldberg.law.function.api.model.GetInputFileSummaryResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetInputFileSummaryFunction @Inject constructor(
    private val fileService: FileService
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.queryParameters}" }
        
        val req = OBJECT_MAPPER.convertValue(request.queryParameters, GetInputFileSummaryRequest::class.java)
        
        // Load metadata
        val summary = fileService.loadFileSummary(req.fileId)

        logger.info { "[${ctx.invocationId}] Successfully retrieved summary for ${req.fileId}: $summary" }
        
        request.createResponseBuilder(HttpStatus.OK)
            .body(GetInputFileSummaryResponse(summary))
            .build()
            
    } catch (ex: Exception) {
        logger.error(ex) { "Error retrieving input file metadata: $request" }
        
        val errorMessage = when (ex) {
            is FileNotFoundException -> ex.message ?: "File not found"
            is IllegalArgumentException -> ex.message ?: "Invalid request parameters"
            else -> "Internal server error: ${ex.message}"
        }
        
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(errorMessage))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetInputFileSummary"
    }
}
