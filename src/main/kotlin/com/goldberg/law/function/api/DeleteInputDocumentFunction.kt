package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.DeleteDocumentRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DeleteInputDocumentFunction(
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), DeleteDocumentRequest::class.java)

        val metadata = dataManager.loadInputMetadata(req.clientName, req.filename)
            ?: throw FileNotFoundException("file ${req.clientName}/${req.filename} does not exist")

        val classificationData: List<ClassifiedPdfMetadata>? = if (metadata.classified) {
            dataManager.loadDocumentClassification(req.clientName, req.filename)
        } else null

        classificationData?.mapAsync {
            dataManager.deleteSplitFile(req.clientName, it)
        }
        logger.info { "Finished deleting split pdf files" }

        if (metadata.analyzed) {
            classificationData?.mapAsync {
                dataManager.deleteModelFile(req.clientName, it)
            }
        }
        logger.info { "Finished deleting analyzed files" }

        if (metadata.statements != null) {
            metadata.statements.mapAsync {
                dataManager.deleteStatementFile(req.clientName, it)
            }
        }

        logger.info { "Finished deleting statements" }

        dataManager.deleteInputFile(req.clientName, req.filename)

        logger.info { "Deleted input file ${req.filename} for client ${req.clientName}" }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(req)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error deleting input file $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "DeleteInputDocument"
    }
}