package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.PutDocumentClassificationRequest
import com.goldberg.law.function.model.request.PutDocumentClassificationResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class PutDocumentClassificationFunction(
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
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), PutDocumentClassificationRequest::class.java)

        val newClassificationData = if (req.overwriteAll) {
            overwriteClassification(req.clientName, req.classification)
        } else {
            overwriteClassificationIndividual(req.clientName, req.classification)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(PutDocumentClassificationResponse(newClassificationData))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error overwriting model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    fun validateClassifications(newClassificationData: List<ClassifiedPdfMetadata>): String {
        if (newClassificationData.isEmpty()) throw RuntimeException("Empty classification provided")
        val filename = newClassificationData[0].filename
        if (!newClassificationData.all { it.filename == filename }) {
            throw RuntimeException("All filenames must match")
        }
        return filename
    }

    fun overwriteClassification(clientName: String, newClassificationData: List<ClassifiedPdfMetadata>): List<ClassifiedPdfMetadata> {
        val filename = validateClassifications(newClassificationData)
        dataManager.saveClassificationData(clientName, filename, newClassificationData)
        logger.info { "putting classification data for ${filename}: $newClassificationData" }
        return newClassificationData
    }

    fun overwriteClassificationIndividual(clientName: String, newClassificationItems: List<ClassifiedPdfMetadata>): List<ClassifiedPdfMetadata> {
        val filename = validateClassifications(newClassificationItems)
        val existingClassification = dataManager.loadDocumentClassification(clientName, filename)

        if (newClassificationItems.all { existingClassification.contains(it) }) return existingClassification

        var newClassificationData = existingClassification.toList()
        newClassificationItems.forEach { classification ->
            newClassificationData = newClassificationData.mapNotNull { cl ->
                cl.copy(pages = cl.pages.minus(classification.pages)).let {
                    if (it.pages.isNotEmpty()) it else null
                }
            }.plus(classification)
        }

        newClassificationData = newClassificationData.sortedBy {
            it.firstPage()
        }

        dataManager.saveClassificationData(clientName, filename, newClassificationData)

        logger.info { "putting classification data for ${filename}: $newClassificationData" }
        return newClassificationData
    }

    companion object {
        const val FUNCTION_NAME = "PutDocumentClassification"
    }
}