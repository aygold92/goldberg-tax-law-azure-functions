package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.agent.model.output.AgentOutputParser
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.GetAgentSessionResultRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetAgentSessionResultFunction @Inject constructor(
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
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), GetAgentSessionResultRequest::class.java)
        val result = agentSessionLauncher.fetchResult(input.sessionId)

        // Serialized here rather than by the worker, so the agent output keeps its snake_case names and ISO dates
        request!!.createResponseBuilder(HttpStatus.OK)
            .header("Content-Type", "application/json")
            .body(AgentOutputParser.JSON.writeValueAsString(result))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error fetching agent session result $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetAgentSessionResult"
    }
}
