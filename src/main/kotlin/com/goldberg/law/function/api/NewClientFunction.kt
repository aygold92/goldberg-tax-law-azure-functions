package com.goldberg.law.function.api

import com.azure.storage.blob.BlobServiceClient
import com.goldberg.law.datamanager.BlobContainer
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.NewClientRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class NewClientFunction(private val blobServiceClient: BlobServiceClient,) {

    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), NewClientRequest::class.java)

        if (!validateClientName(req.clientName)) {
            throw RuntimeException("""
                "Invalid client name: ${req.clientName}.  Rules: 
                * between 3-52 characters
                * only lower case letters, numbers, and hyphens 
                * can't start or end with a hyphen
                * can't have consecutive hyphens")
                """.trimMargin())
        }

        // create all the containers
        BlobContainer.entries.forEach { containerName ->
            val clientContainerName = containerName.forClient(req.clientName)
            blobServiceClient.createBlobContainerIfNotExists(clientContainerName)
            logger.info { "created container $clientContainerName" }
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(req)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error creating new client for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    /**
     *   * Container names must start or end with a letter or number, and can contain only letters, numbers, and the hyphen/minus (-) character.
     *   * Every hyphen/minus (-) character must be immediately preceded and followed by a letter or number; consecutive hyphens aren't permitted in container names.
     *   * All letters in a container name must be lowercase.
     *   * Container names must be from 3 through 63 characters long.
     * https://learn.microsoft.com/en-us/rest/api/storageservices/naming-and-referencing-containers--blobs--and-metadata
     */
    fun validateClientName(clientName: String): Boolean {
        val regex = "^[a-z0-9](?:[a-z0-9-]{1,${CLIENT_NAME_MAX_LENGTH - 2}}[a-z0-9])?\$".toRegex()
        return clientName.length in 3..CLIENT_NAME_MAX_LENGTH && regex.matches(clientName) && !clientName.contains("--")
    }

    companion object {
        const val FUNCTION_NAME = "NewClient"
        // blob container max length is 63, and we will add "-statements" to it
        private const val CLIENT_NAME_MAX_LENGTH = 52
    }
}