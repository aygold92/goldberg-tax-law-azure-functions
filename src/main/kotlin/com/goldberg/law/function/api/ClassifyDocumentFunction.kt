package com.goldberg.law.function.api

import com.goldberg.law.function.activity.ClassifyDocumentActivity
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityInput
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityOutput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class ClassifyDocumentFunction(private val classifyDocumentActivity: ClassifyDocumentActivity) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val input = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), ClassifyDocumentActivityInput::class.java)
        val output: ClassifyDocumentActivityOutput = classifyDocumentActivity.classifyDocument(input, ctx)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(output)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error classifying document $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "ClassifyDocument"
    }
}
