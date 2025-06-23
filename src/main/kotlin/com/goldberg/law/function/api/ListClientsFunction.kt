package com.goldberg.law.function.api

import com.azure.storage.blob.BlobServiceClient
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.ListClientsResponse
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListClientsFunction(private val blobServiceClient: BlobServiceClient,) {

    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ListClientsRequest" }

        val containers = blobServiceClient.listBlobContainers().toList()
        // TODO: should we handle partially created clients?
        val clients = containers.map { it.name.substringBeforeLast("-") }.toSet()

        request.createResponseBuilder(HttpStatus.OK)
            .body(ListClientsResponse(clients))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error listing clients" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListClients"
    }
}