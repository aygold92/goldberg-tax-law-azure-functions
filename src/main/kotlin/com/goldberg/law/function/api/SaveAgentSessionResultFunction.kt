package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentResultSaver
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.SaveAgentSessionResultRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

/**
 * Writes a finished session's output to the database. The caller polls [GetAgentSessionResultFunction] and
 * calls this once it reports COMPLETED; a session that hasn't finished is reported back and nothing is
 * written, so calling it early is harmless.
 *
 * Calling it twice is harmless too: a target that already holds records is reported back untouched, so a
 * second call can't discard manual edits. Pass `override` to replace them anyway.
 */
class SaveAgentSessionResultFunction @Inject constructor(
    private val agentResultSaver: AgentResultSaver,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), SaveAgentSessionResultRequest::class.java)
        val result = agentResultSaver.save(input.sessionId, input.override)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(result)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error saving agent session result $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "SaveAgentSessionResult"
    }
}
