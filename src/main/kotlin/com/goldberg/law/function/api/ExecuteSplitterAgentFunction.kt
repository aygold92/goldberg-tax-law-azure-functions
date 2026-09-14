package com.goldberg.law.function.api

import com.goldberg.law.agent.AgentSessionLauncher
import com.goldberg.law.database.service.FileService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.ExecuteSplitterAgentRequest
import com.goldberg.law.function.api.model.ExecuteSplitterAgentResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ExecuteSplitterAgentFunction @Inject constructor(
    private val fileService: FileService,
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
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ExecuteSplitterAgentRequest::class.java)
        val (sessionId, anthropicFileId) = agentSessionLauncher.startSplitter(fileService.loadFile(input.fileId))

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(ExecuteSplitterAgentResponse(sessionId, anthropicFileId))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error starting splitter agent $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ExecuteSplitterAgent"
    }
}
