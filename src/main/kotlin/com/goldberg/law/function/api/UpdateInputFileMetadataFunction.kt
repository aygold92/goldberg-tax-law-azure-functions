package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.function.model.request.UpdateInputFileMetadataRequest
import com.goldberg.law.function.model.request.UpdateInputFileMetadataResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class UpdateInputFileMetadataFunction(
    private val dataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), UpdateInputFileMetadataRequest::class.java)
        
        // Validate request
        validateRequest(req)
        
        // Check if file exists by loading current metadata
        val existingMetadata = dataManager.loadInputMetadata(req.clientName, req.filename)
            ?: throw FileNotFoundException("File ${req.clientName}/${req.filename} does not exist")
        
        logger.info { "[${ctx.invocationId}] Updating metadata for ${req.filename} from $existingMetadata to ${req.metadata}" }
        
        // Update the metadata
        dataManager.updateInputPdfMetadata(req.clientName, req.filename, req.metadata)
        
        logger.info { "[${ctx.invocationId}] Successfully updated metadata for ${req.filename}" }
        
        request!!.createResponseBuilder(HttpStatus.OK)
            .body(UpdateInputFileMetadataResponse.success(req.metadata))
            .build()
            
    } catch (ex: Exception) {
        logger.error(ex) { "Error updating input file metadata: $request" }
        
        val errorMessage = when (ex) {
            is FileNotFoundException -> ex.message ?: "File not found"
            is IllegalArgumentException -> ex.message ?: "Invalid request parameters"
            else -> "Internal server error: ${ex.message}"
        }
        
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(UpdateInputFileMetadataResponse.failed(errorMessage))
            .build()
    }
    
    private fun validateRequest(req: UpdateInputFileMetadataRequest) {
        if (req.clientName.isBlank()) {
            throw IllegalArgumentException("clientName cannot be blank")
        }
        if (req.filename.isBlank()) {
            throw IllegalArgumentException("filename cannot be blank")
        }
    }

    companion object {
        const val FUNCTION_NAME = "UpdateInputFileMetadata"
    }
}
