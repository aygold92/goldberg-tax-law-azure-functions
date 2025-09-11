package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.function.model.request.GetInputFileMetadataRequest
import com.goldberg.law.function.model.request.GetInputFileMetadataResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetInputFileMetadataFunction(
    private val dataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.queryParameters}" }
        
        val req = OBJECT_MAPPER.convertValue(request.queryParameters, GetInputFileMetadataRequest::class.java)
        
        // Validate request
        validateRequest(req)
        
        // Load metadata
        val metadata = dataManager.loadInputMetadata(req.clientName, req.filename)
            ?: throw FileNotFoundException("File ${req.clientName}/${req.filename} does not exist")
        
        logger.info { "[${ctx.invocationId}] Successfully retrieved metadata for ${req.filename}: $metadata" }
        
        request.createResponseBuilder(HttpStatus.OK)
            .body(GetInputFileMetadataResponse.success(metadata))
            .build()
            
    } catch (ex: Exception) {
        logger.error(ex) { "Error retrieving input file metadata: $request" }
        
        val errorMessage = when (ex) {
            is FileNotFoundException -> ex.message ?: "File not found"
            is IllegalArgumentException -> ex.message ?: "Invalid request parameters"
            else -> "Internal server error: ${ex.message}"
        }
        
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(GetInputFileMetadataResponse.failed(errorMessage))
            .build()
    }
    
    private fun validateRequest(req: GetInputFileMetadataRequest) {
        if (req.clientName.isBlank()) {
            throw IllegalArgumentException("clientName cannot be blank")
        }
        if (req.filename.isBlank()) {
            throw IllegalArgumentException("filename cannot be blank")
        }
    }

    companion object {
        const val FUNCTION_NAME = "GetInputFileMetadata"
    }
}
