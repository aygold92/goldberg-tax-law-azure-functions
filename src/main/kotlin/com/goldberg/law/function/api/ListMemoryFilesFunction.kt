package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.agent.MemoryConsolidation
import com.goldberg.law.function.api.model.ApiResult
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

/**
 * Lists one memory store's folders and files, without their content: `?memory=SPLITTING` or `?memory=EXTRACTION`,
 * the store named for the agent that writes it.
 */
class ListMemoryFilesFunction @Inject constructor(
    private val agentSessionLauncher: AgentSessionLauncher,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.FUNCTION)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val memory = MemoryConsolidation.valueOf(
            request!!.queryParameters["memory"] ?: throw IllegalArgumentException("Missing required query parameter: memory")
        )
        logger.info { "[${ctx.invocationId}] listing memory files for $memory" }

        request.createResponseBuilder(HttpStatus.OK)
            .body(agentSessionLauncher.listMemory(memory))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error listing memory files for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ListMemoryFiles"
    }
}
