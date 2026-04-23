package com.goldberg.law.function.api

import com.goldberg.law.database.service.FileService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.FetchReadSASTokenRequest
import com.goldberg.law.function.api.model.FetchReadSASTokenResponse
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import com.microsoft.azure.functions.*
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class FetchReadSASTokenFunction @Inject constructor(
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
        val req = OBJECT_MAPPER.readValue(request.body?.orElseThrow(), FetchReadSASTokenRequest::class.java)

        val file = fileService.loadFile(req.fileId)
        val result = azureStorageDataManager.generateReadSasToken(file)

        request.createResponseBuilder(HttpStatus.OK)
            .body(FetchReadSASTokenResponse(token = result.token, storageLocation = result.storageLocation))
            .build()
    } catch (ex: Exception) {
        logger.error(ex) { "Error fetching read SAS token for input $request" }
        request.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    companion object {
        const val FUNCTION_NAME = "FetchReadSASToken"
    }
}
