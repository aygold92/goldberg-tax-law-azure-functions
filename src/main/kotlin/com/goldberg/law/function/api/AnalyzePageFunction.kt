package com.goldberg.law.function.api

import com.goldberg.law.AppModule
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.AnalyzePagesRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.google.inject.Inject
import com.google.inject.name.Named
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class AnalyzePageFunction @Inject constructor(
    private val classificationService: ClassificationService,
    private val processDataModelActivity: ProcessDataModelActivity,
    @Named(AppModule.NUM_FUNCTION_WORKERS) private val numWorkers: Int,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), AnalyzePagesRequest::class.java)

        val results = classificationService.loadClassifications(req.pageRequests).chunked(numWorkers).flatMap { classifications ->
                classifications.mapAsync {
                    val input = ProcessDataModelActivityInput(requestId = ctx.invocationId, classification = it)
                    processDataModelActivity.processDataModel(input, ctx)
                }
            }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(results)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error analyzing for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "AnalyzePages"
    }
}