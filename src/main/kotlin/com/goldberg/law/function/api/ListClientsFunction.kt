package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClientService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.ListClientsResponse
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListClientsFunction @Inject constructor(private val clientService: ClientService) {

    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ListClientsRequest" }

        val clients = clientService.listClients()

        request.createResponseBuilder(HttpStatus.OK)
            .body(ListClientsResponse(clients))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error listing clients" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListClients"
    }
}