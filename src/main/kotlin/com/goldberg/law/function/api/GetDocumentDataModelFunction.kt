package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.ApiResult
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class GetDocumentDataModelFunction @Inject constructor(
    private val dataManager: AzureStorageDataManager,
    private val classificationService: ClassificationService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val classificationId = UUID.fromString(request!!.queryParameters["classificationId"]
            ?: throw IllegalArgumentException("Missing required query parameter: classificationId"))
        logger.info { "[${ctx.invocationId}] loading data model for classificationId=$classificationId" }

        val classification = classificationService.loadClassification(classificationId)
        val model = dataManager.loadModel(classification)

        request.createResponseBuilder(HttpStatus.OK)
            .body(model)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error loading model for $request" }
        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetDocumentDataModel"
    }
}
