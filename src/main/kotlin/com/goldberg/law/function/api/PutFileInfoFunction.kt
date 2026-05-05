package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.entity.InputFile
import com.goldberg.law.entity.InputFileInfo
import com.goldberg.law.function.api.model.ApiResult
import com.goldberg.law.function.api.model.PutFileInfoRequest
import com.goldberg.law.function.api.model.PutFileInfoResponse
import com.goldberg.law.function.model.EventSchema
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.withoutExtension
import java.security.MessageDigest
import com.google.inject.Inject
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.HttpMethod
import com.microsoft.azure.functions.HttpRequestMessage
import com.microsoft.azure.functions.HttpResponseMessage
import com.microsoft.azure.functions.HttpStatus
import com.microsoft.azure.functions.annotation.AuthorizationLevel
import com.microsoft.azure.functions.annotation.EventGridTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.azure.functions.annotation.HttpTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.net.URI
import java.time.Instant
import java.util.*

class PutFileInfoFunction @Inject constructor(
    private val clientService: ClientService,
    private val fileService: FileService,
    private val azureStorageDataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun runPutFileInfoHttp(
        @HttpTrigger(name = "req", methods = [HttpMethod.POST], authLevel = AuthorizationLevel.ANONYMOUS)
        request: HttpRequestMessage<Optional<String?>?>?,
        ctx: ExecutionContext
    ): HttpResponseMessage = try {
        logger.info { "[${ctx.invocationId}] processing ${request?.body?.orElseThrow()}" }
        val req = OBJECT_MAPPER.readValue(request?.body?.orElseThrow(), PutFileInfoRequest::class.java)
        val response = putFileInfo(ctx, req)

        request!!.createResponseBuilder(HttpStatus.OK)
            .body(response)
            .build()
    } catch (ex: Exception) {
        // TODO: different error codes
        logger.error(ex) { "Error putting new file info $request" }

        request!!.createResponseBuilder(HttpStatus.BAD_REQUEST)
            .body(ApiResult.failed(ex))
            .build()
    }

    @FunctionName(FUNCTION_NAME_EVENT_GRID)
    fun runEventGrid(
        @EventGridTrigger(name = "eventGridEvent") event: EventSchema,
        ctx: ExecutionContext
    ) {
        try {
            logger.info { "[${ctx.invocationId}] Processing Event Grid event: ${event.id}" }
            
            // Only process BlobCreated events
            if (event.eventType != "Microsoft.Storage.BlobCreated") {
                logger.debug { "[${ctx.invocationId}] Ignoring event type: ${event.eventType}" }
                return
            }

            // Extract event data
            val eventId = UUID.fromString(event.id)
            val data = event.data

            // Extract blob URL from event data
            val blobUrl = data.url

            logger.info { "[${ctx.invocationId}] Processing blob created event for: $blobUrl" }

            // Parse blob URL to extract container name and blob path
            val uri = URI(blobUrl)
            val pathParts = uri.path.removePrefix("/").split("/", limit = 3)
            if (pathParts.size != 3) {
                throw IllegalArgumentException("Invalid blob URL format: $blobUrl")
            }

            val containerName = pathParts[0] // This is the clientId (UUID)
            val folder = pathParts[1] // e.g., "uploads"
            val filename = pathParts[2] // e.g. filename.pdf

            if (folder != AzureStorageDataManager.UPLOAD_FOLDER) {
                throw IllegalArgumentException("Blob $blobUrl must be in '${AzureStorageDataManager.UPLOAD_FOLDER}/' folder, but found: $folder")
            }

            if (!filename.endsWith(".pdf")) {
                throw IllegalArgumentException("Filename for blob $blobUrl must be a PDF file.  Found: $filename")
            }

            // Parse container name as UUID (clientId)
            val clientId = try {
                UUID.fromString(containerName)
            } catch (ex: Exception) {
                throw IllegalArgumentException("Container name must be a valid UUID (clientId): $containerName", ex)
            }

            putFileInfo(ctx, PutFileInfoRequest(
                filename = filename,
                clientId = clientId,
                requestToken = eventId
            ))
        } catch (ex: Exception) {
            logger.error(ex) { "[${ctx.invocationId}] Error processing Event Grid event: ${event.id}" }
            throw ex
        }
    }

    private fun putFileInfo(ctx: ExecutionContext, req: PutFileInfoRequest): PutFileInfoResponse {
        val client = clientService.loadClient(req.clientId)
        val filename = req.filename.withoutExtension()
        logger.info { "[${ctx.invocationId}] Found client: ${client.clientName} (ID: ${client.clientId})" }

        val blobBytes = azureStorageDataManager.loadUploadedPdfBytes(req.clientId, filename)

        val pdfDocument = try {
            Loader.loadPDF(blobBytes)
        } catch (ex: Exception) {
            throw InvalidPdfException("Unable to load PDF for file ${req.clientId}/$filename: $ex")
        }

        val contentHash = UUID.nameUUIDFromBytes(MessageDigest.getInstance("SHA-256").digest(blobBytes))

        val inputFile = InputFile(
            client = client,
            info = InputFileInfo(
                fileId = UUID.randomUUID(), // placeholder — DB generates the real ID
                fileName = filename,
                contentHash = contentHash,
                uploadedAt = Instant.now().toEpochMilli(),
                numPages = pdfDocument.numberOfPages
            )
        )
        // Insert into DB first so the DB generates the fileId
        val fileId = fileService.insertFile(
            inputFile = inputFile,
            requestToken = req.requestToken
        )

        // Copy blob to input folder using the DB-generated fileId; rollback DB on failure
        try {
            azureStorageDataManager.saveInputPdf(inputFile.withFileId(fileId), blobBytes)
        } catch (ex: Exception) {
            logger.error(ex) { "[${ctx.invocationId}] Blob save failed, rolling back DB insert for fileId=$fileId" }
            fileService.deleteInputFile(fileId)
            throw ex
        }

        // Delete the upload blob — non-critical, log and continue on failure
        try {
            azureStorageDataManager.deleteUploadBlob(req.clientId, filename)
        } catch (ex: Exception) {
            logger.warn(ex) { "[${ctx.invocationId}] Failed to delete upload blob ${filename}, continuing" }
        }

        logger.info { "[${ctx.invocationId}] Successfully processed file: ${req.filename} for client: ${client.clientName} with ID: $fileId" }
        return PutFileInfoResponse(fileId)
    }

    companion object {
        const val FUNCTION_NAME = "PutFileInfo"
        const val FUNCTION_NAME_EVENT_GRID = "$FUNCTION_NAME-eventGrid"
    }
}

