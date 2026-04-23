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

class LoadBankStatementFunction @Inject constructor(
    private val statementService: StatementService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val statementId = UUID.fromString(request!!.queryParameters["statementId"]
            ?: throw IllegalArgumentException("Missing required query parameter: statementId"))
        logger.info { "[${ctx.invocationId}] loading bank statement statementId=$statementId" }

        val statement = statementService.loadBankStatement(statementId)

        request.createResponseBuilder(HttpStatus.OK)
            .body(statement)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error loading bank statement for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "LoadBankStatement"
    }
}
