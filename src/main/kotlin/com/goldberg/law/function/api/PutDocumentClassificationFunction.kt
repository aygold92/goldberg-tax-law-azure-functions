package com.goldberg.law.function.api

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.function.api.model.AnalyzeDocumentResult
import com.goldberg.law.function.api.model.PutDocumentClassificationRequest
import com.goldberg.law.function.api.model.PutDocumentClassificationResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class PutDocumentClassificationFunction @Inject constructor(
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
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), PutDocumentClassificationRequest::class.java)

        val newInfos = DbExec.txnSafe {
            classificationService.deleteClassifications(req.classificationsToRemove)
            classificationService.insertClassifications(req.file)
        }

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(PutDocumentClassificationResponse(newInfos))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error overwriting model $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "PutDocumentClassification"
    }
}