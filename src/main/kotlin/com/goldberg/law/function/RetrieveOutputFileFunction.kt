/**
 * Azure Function for retrieving output files from Azure Storage.
 * 
 * This function retrieves CSV files that were previously generated and stored in Azure Blob Storage.
 * It's used by the frontend to download CSV content for Google Sheets integration or direct download.
 * 
 * The function takes a client name and file name, then uses the AzureStorageDataManager to
 * retrieve the file content from the OUTPUT container.
 * 
 * Dependencies:
 * - Azure Functions Core Library
 * - AzureStorageDataManager for blob operations
 */

package com.goldberg.law.function

import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.model.request.RetrieveOutputFileRequest
import com.goldberg.law.function.model.request.RetrieveOutputFileResponse
import java.util.*

class RetrieveOutputFileFunction(
    private val dataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}
    private val objectMapper = ObjectMapper()

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.body.orElseThrow()}" }
        val req = objectMapper.readValue(request.body.orElseThrow(), RetrieveOutputFileRequest::class.java)

        val fileContent = dataManager.loadOutputFile(req.clientName, req.fileName)

        request.createResponseBuilder(HttpStatus.OK)
            .body(RetrieveOutputFileResponse(
                status = "SUCCESS",
                fileName = req.fileName,
                content = fileContent
            ))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Failed to retrieve output file" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(RetrieveOutputFileResponse(
                status = "ERROR",
                errorMessage = ex.message ?: "Unknown error occurred"
            ))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "RetrieveOutputFile"
    }
}

