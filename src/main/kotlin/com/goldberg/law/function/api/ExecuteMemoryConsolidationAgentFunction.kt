package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.ExecuteMemoryConsolidationAgentRequest
import com.goldberg.law.function.api.model.ExecuteMemoryConsolidationAgentResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ExecuteMemoryConsolidationAgentFunction @Inject constructor(
    private val agentSessionLauncher: AgentSessionLauncher,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ExecuteMemoryConsolidationAgentRequest::class.java)
        val (runId, sessionId) = agentSessionLauncher.startMemoryConsolidation(input.memory)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ExecuteMemoryConsolidationAgentResponse(runId, sessionId))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error starting memory consolidation agent $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ExecuteMemoryConsolidationAgent"
    }
}
