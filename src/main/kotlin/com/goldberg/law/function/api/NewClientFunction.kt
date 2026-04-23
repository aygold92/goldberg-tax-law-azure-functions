package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClientService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.NewClientRequest
import com.goldberg.law.function.api.model.NewClientResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class NewClientFunction @Inject constructor(
    private val azureStorageDataManager: AzureStorageDataManager,
    private val clientService: ClientService,
) {

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
                * between 3-63 characters
                * only lower case letters, numbers, and hyphens 
                * can't start or end with a hyphen
                * can't have consecutive hyphens")
                """.trimMargin())
        }

        // Insert client into MySQL first (with idempotency check)
        val clientId = clientService.insertClient(req.clientName, req.requestToken)
        logger.info { "Client created/retrieved with ID: $clientId" }

        // create container for storing files
        azureStorageDataManager.createClientContainerIfNotExists(clientId)
        logger.info { "created container $req.clientName" }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(NewClientResponse(clientId, req.clientName))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error creating new client for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    /**
     *   * Client names must start or end with a letter or number, and can contain only letters, numbers, and the hyphen/minus (-) character.
     *   * Every hyphen/minus (-) character must be immediately preceded and followed by a letter or number
     *   * Client names must be from 3 through 63 characters long.
     */
    fun validateClientName(clientName: String): Boolean {
        val regex = "^[A-Za-z0-9](?:[A-Za-z0-9-]{1,${CLIENT_NAME_MAX_LENGTH - 2}}[A-Za-z0-9])?$".toRegex()
        return clientName.length in 3..CLIENT_NAME_MAX_LENGTH && regex.matches(clientName) && !clientName.contains("--")
    }

    companion object {
        const val FUNCTION_NAME = "NewClient"
        private const val CLIENT_NAME_MAX_LENGTH = 63
    }
}