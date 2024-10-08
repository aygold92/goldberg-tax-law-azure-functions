package com.goldberg.law.function

import com.azure.core.util.Configuration
import com.azure.storage.blob.BlobContainerClientBuilder
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.BlobServiceClientBuilder
import com.azure.storage.blob.sas.BlobContainerSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import com.goldberg.law.function.model.request.AnalyzeDocumentResult
import com.goldberg.law.function.model.request.SASTokenRequest
import com.goldberg.law.function.model.request.SASTokenResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.util.*
import javax.inject.Inject

class FetchSASTokenFunction @Inject constructor(
    private val blobServiceClient: BlobServiceClient,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.GET], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String>>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.queryParameters.toStringDetailed()}" }
        val req = OBJECT_MAPPER.convertValue(request.queryParameters, SASTokenRequest::class.java)

        // TODO: replace with login
        if (req.token != SPECIAL_PASSWORD) {
            throw IllegalArgumentException("Invalid password")
        }

        val token: String = when (req.action) {
            // TODO: probably fix these actions
            "input" -> blobServiceClient.getBlobContainerClient("input")
                .generateSas(
                    BlobServiceSasSignatureValues(
                        OffsetDateTime.now().plusMinutes(15), BlobContainerSasPermission()
                            .setListPermission(true)
                            .setReadPermission(true)
                            .setWritePermission(true)
                            .setDeletePermission(true)
                    )
                )

            "output" ->
                blobServiceClient.getBlobContainerClient("output")
                    .generateSas(
                        BlobServiceSasSignatureValues(
                            OffsetDateTime.now().plusMinutes(15), BlobContainerSasPermission()
                                .setListPermission(true)
                                .setReadPermission(true)
                                .setWritePermission(true)
                        )
                    )
            else -> throw IllegalArgumentException("Invalid action ${req.action}")
        }

        request.createResponseBuilder(HttpStatus.OK)
            .body(SASTokenResponse(token))
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        // TODO: don't log sensitive information
        logger.error(ex) { "Error fetch SAS token for input $request" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(AnalyzeDocumentResult.failed(ex))
            .build()
    }
    companion object {
        const val FUNCTION_NAME = "RequestSASToken"
        const val SPECIAL_PASSWORD = "pleasegivemeatoken"
    }
}