package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.LoadTransactionsFromModelRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class LoadTransactionsFromModelFunction @Inject constructor(
    private val classificationService: ClassificationService,
    private val azureStorageDataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), LoadTransactionsFromModelRequest::class.java)
        val classification = classificationService.loadClassification(req.classificationId)
        val model = azureStorageDataManager.loadModel(classification)
        if (model is StatementDataModel) {
            request!!.createResponseBuilder(HttpStatus.OK)
                .body(model.getTransactionRecords())
                .build()
        } else {
            val type = if (model.isCheck()) "check" else "extra pages"
            throw RuntimeException("Model ${req.classificationId} was classified as type '$type', please re-analyze the page first")
        }
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error loading model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "LoadTransactionsFromModel"
    }
}