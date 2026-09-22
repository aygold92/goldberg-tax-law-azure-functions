package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.agent.AgentSessionStarter
import com.goldberg.law.agent.AgentStart
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.ExecuteAgentResponse
import com.goldberg.law.function.api.model.ExecuteCheckExtractionAgentRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ExecuteCheckExtractionAgentFunction @Inject constructor(
    private val agentSessionStarter: AgentSessionStarter,
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
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ExecuteCheckExtractionAgentRequest::class.java)
        val start = if (input.classificationId != null) {
            agentSessionStarter.startCheckExtraction(input.classificationId, input.overrideExisting)
        } else {
            // One-off run against raw page numbers: nothing to record it against
            val (anthropicFileId, pages) = input.requireRawParameters()
            AgentStart(agentSessionLauncher.startCheckExtraction(anthropicFileId, pages), started = true)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ExecuteAgentResponse(start.sessionId, start.started))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error starting check extraction agent $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ExecuteCheckExtractionAgent"
    }
}
