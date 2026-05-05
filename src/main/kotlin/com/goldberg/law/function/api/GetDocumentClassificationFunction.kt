package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.function.api.model.ApiResult
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
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val fileId = UUID.fromString(request!!.queryParameters["fileId"]
            ?: throw IllegalArgumentException("Missing required query parameter: fileId"))
        logger.info { "[${ctx.invocationId}] loading classifications for fileId=$fileId" }

        val classifications = classificationService.loadClassifications(fileId).map { it.info }

        request.createResponseBuilder(HttpStatus.OK)
            .body(classifications)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error loading classifications for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetDocumentClassification"
    }
}
