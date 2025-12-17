package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.GetDocumentDataModelRequest
import com.goldberg.law.util.OBJECT_MAPPER
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
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), GetDocumentDataModelRequest::class.java)
        val classification = classificationService.loadClassification(req.classificationId)
        val model = dataManager.loadModel(classification)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(model)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error loading model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "GetDocumentDataModel"
    }
}