package com.goldberg.law.function.api

import com.goldberg.law.database.service.StatementService
import com.goldberg.law.function.api.model.ApiResult
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ListStatementsFunction @Inject constructor(
    private val statementService: StatementService,
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
        val statementIds = request.queryParameters["statementIds"]
            ?.split(",")
            ?.map { UUID.fromString(it.trim()) }
        logger.info { "[${ctx.invocationId}] listing statements for clientId=$clientId statementIds=$statementIds" }

        val statements = statementService.listBankStatements(clientId, statementIds)

        request.createResponseBuilder(HttpStatus.OK)
            .body(statements)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing statements for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListStatements"
    }
}
