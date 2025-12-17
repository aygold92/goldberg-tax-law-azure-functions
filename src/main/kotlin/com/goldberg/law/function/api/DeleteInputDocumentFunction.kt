package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.DeleteDocumentRequest
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.mapAsync
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DeleteInputDocumentFunction @Inject constructor(
    private val dataManager: AzureStorageDataManager,
    private val fileService: FileService,
    private val classificationService: ClassificationService
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

        val inputFile = fileService.loadFile(req.fileId)
        val classifications = classificationService.loadClassifications(inputFile.fileId)
        fileService.deleteInputFileWithData(inputFile.fileId)
        logger.info { "Deleting all data from the database" }

        classifications.mapAsync { dataManager.deleteSplitFile(it) }
        logger.info { "Finished deleting split pdf files" }

        classifications.mapAsync { if (it.info.modelLocation != null) dataManager.deleteModelFile(it) }
        logger.info { "Finished deleting analyzed model files" }

        dataManager.deleteInputFile(inputFile)

        logger.info { "Deleted input file ${req.fileId} (${inputFile.fileName}) for client ${inputFile.client}" }

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