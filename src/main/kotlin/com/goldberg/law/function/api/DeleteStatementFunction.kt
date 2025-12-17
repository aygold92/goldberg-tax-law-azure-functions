package com.goldberg.law.function.api

import com.goldberg.law.database.service.StatementService
import com.goldberg.law.function.api.model.DeleteStatementRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DeleteStatementFunction @Inject constructor(
    private val statementService: StatementService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), DeleteStatementRequest::class.java)

        statementService.deleteBankStatementWithData(req.statementId)

        logger.info { "Deleted statement file ${req.statementId}" }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(mapOf("message" to "Statement deleted successfully"))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error deleting statement for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(mapOf("error" to ex.message))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "DeleteStatement"
    }
}
