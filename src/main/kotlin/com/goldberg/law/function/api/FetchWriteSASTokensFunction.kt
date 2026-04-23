package com.goldberg.law.function.api

import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.sas.BlobSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import com.goldberg.law.database.service.FileService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.FetchWriteSASTokensRequest
import com.goldberg.law.function.api.model.FetchWriteSASTokensResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.withoutExtension
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.time.OffsetDateTime
import java.util.*
import javax.inject.Inject

class FetchWriteSASTokensFunction @Inject constructor(
    private val azureStorageDataManager: AzureStorageDataManager,
    private val fileService: FileService,
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request.body?.orElseThrow(), FetchWriteSASTokensRequest::class.java)

        val invalidFilenames = req.filenames.filterNot { it.endsWith(".pdf") }
        if (invalidFilenames.isNotEmpty()) {
            throw IllegalArgumentException("All files must be pdfs ending in .pdf: $invalidFilenames")
        }

        val filenames = req.filenames.map { it.withoutExtension() }
        val alreadyExist = fileService.fileNamesExist(req.clientId, filenames)

        val tokens = (filenames - alreadyExist).associateWith { filename ->
            azureStorageDataManager.generateWriteSasToken(req.clientId, filename.withoutExtension())
        }

        request.createResponseBuilder(HttpStatus.OK)
            .body(FetchWriteSASTokensResponse(tokens = tokens, alreadyExist = alreadyExist))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error fetching write SAS tokens for input $request" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "FetchWriteSASTokens"
    }
}
