package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.activity.ProcessStatementsActivity
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.activity.ProcessStatementsActivityInput
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.MatchStatementsWithChecksRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class MatchStatementsWithChecksFunction(
    private val dataManager: AzureStorageDataManager,
    private val processStatementsActivity: ProcessStatementsActivity,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), MatchStatementsWithChecksRequest::class.java)

        // Load models for all statements and checks
        val statementModels = req.statements.map { pdfMetadata ->
            DocumentDataModelContainer(dataManager.loadModel(req.clientName, pdfMetadata))
        }
        val checkModels = req.checks.map { pdfMetadata ->
            DocumentDataModelContainer(dataManager.loadModel(req.clientName, pdfMetadata))
        }

        // Combine all models
        val allModels = (statementModels + checkModels).toSet()

        // Build metadata map by loading metadata for each unique filename
        val uniqueFilenames = (req.statements + req.checks).map { it.filename }.distinct()
        val metadataMap = uniqueFilenames.associate { filename ->
            val metadata = dataManager.loadInputMetadata(req.clientName, filename) ?: InputFileMetadata(statements = setOf())
            filename to metadata
        }

        // Create activity input
        val activityInput = ProcessStatementsActivityInput(
            requestId = ctx.invocationId,
            clientName = req.clientName,
            documentDataModels = allModels,
            metadataMap = metadataMap,
            keepOriginalMetadata = true
        )

        // Process statements and checks
        val result = processStatementsActivity.processStatementsAndChecks(activityInput, ctx)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(result)
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error matching statements with checks $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "MatchStatementsWithChecks"
    }
}

