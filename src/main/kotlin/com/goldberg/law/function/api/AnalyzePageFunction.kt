package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.function.activity.ProcessDataModelActivity
import com.goldberg.law.function.activity.ProcessStatementsActivity
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.AnalyzePagesRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class AnalyzePageFunction(
    private val dataManager: AzureStorageDataManager,
    private val processDataModelActivity: ProcessDataModelActivity,
    private val processStatementsActivity: ProcessStatementsActivity
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), AnalyzePagesRequest::class.java)

        // TODO: how does reclassifying work?
        // TODO: maximum of 15 concurrent requests
        val models = req.pageRequests.mapAsync {
            val input = it.copy(requestId = "${it.requestId} - ${it.classifiedPdfMetadata}", useOriginalFile = true)
            processDataModelActivity.processDataModel(input, ctx)
        }

        models.zip(req.pageRequests).forEach { (model, req) ->
            val filename = req.classifiedPdfMetadata.filename
            val metadata = dataManager.loadInputMetadata(req.clientName, filename)
                ?: throw FileNotFoundException("file ${req.clientName}/$filename does not exist")

            processStatementsActivity.processStatementsAndChecks(
                ProcessStatementsActivityInput(
                    ctx.invocationId!!, req.clientName, setOf(model), mapOf(filename to metadata), true
                ), ctx)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(models)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error analyzing for input $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "AnalyzePage"
    }
}