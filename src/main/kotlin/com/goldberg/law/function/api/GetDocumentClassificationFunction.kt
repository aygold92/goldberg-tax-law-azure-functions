package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.GetDocumentClassificationRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetDocumentClassificationFunction @Inject constructor(
    private val classificationService: ClassificationService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), GetDocumentClassificationRequest::class.java)
        val classifications = classificationService.loadClassifications(req.fileId)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(classifications)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error loading model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetDocumentClassification"
    }
}