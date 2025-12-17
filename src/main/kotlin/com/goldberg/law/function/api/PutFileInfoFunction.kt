package com.goldberg.law.function.api

import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.datamanager.Extension
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.entity.InputFile
import com.goldberg.law.entity.InputFileInfo
import com.goldberg.law.function.model.EventSchema
import com.google.inject.Inject
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.EventGridTrigger
import com.microsoft.azure.functions.annotation.FunctionName
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.net.URI
import java.util.*

class PutFileInfoFunction @Inject constructor(
    private val clientService: ClientService,
    private val fileService: FileService,
    private val azureStorageDataManager: AzureStorageDataManager
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun run(
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

            processEvent(event, ctx)
        } catch (ex: Exception) {
            logger.error(ex) { "[${ctx.invocationId}] Error processing Event Grid event: ${event.id}" }
            throw ex
        }
    }

    private fun processEvent(event: EventSchema, ctx: ExecutionContext) {
        try {
            // Extract event data
            val eventId = UUID.fromString(event.id)
            val data = event.data
            
            // Extract blob URL from event data
            val blobUrl = data.url
            
            logger.info { "[${ctx.invocationId}] Processing blob created event for: $blobUrl" }

            // Parse blob URL to extract container name and blob path
            val uri = URI(blobUrl)
            val pathParts = uri.path.removePrefix("/").split("/", limit = 2)
            if (pathParts.size < 2) {
                throw IllegalArgumentException("Invalid blob URL format: $blobUrl")
            }
            
            val containerName = pathParts[0] // This is the clientId (UUID)
            val blobPath = pathParts[1] // e.g., "input/filename.pdf"
            
            // Extract filename from blob path (remove "input/" prefix)
            val filename = if (blobPath.startsWith("input/")) {
                blobPath.removePrefix("input/")
            } else {
                throw IllegalArgumentException("Blob must be in 'input/' folder, but found: $blobPath")
            }

            // Parse container name as UUID (clientId)
            val clientId = try {
                UUID.fromString(containerName)
            } catch (ex: Exception) {
                throw IllegalArgumentException("Container name must be a valid UUID (clientId): $containerName", ex)
            }

            // Load client by ID
            val client = clientService.loadClient(clientId)
            logger.info { "[${ctx.invocationId}] Found client: ${client.clientName} (ID: ${client.clientId})" }

            // Load blob bytes from Azure Storage
            val blobBytes = azureStorageDataManager.loadBlobBytes(containerName, blobPath)
            
            // Validate PDF
            val pdfDocument = try {
                Loader.loadPDF(blobBytes)
            } catch (ex: Exception) {
                throw InvalidPdfException("Unable to load PDF from blob $blobUrl: $ex")
            }

            // Create StorageLocation
            val storageLocation = StorageLocation(
                containerName = containerName,
                filePath = blobPath.removeSuffix(".pdf"),
                extension = Extension.PDF
            )

            // Create temporary InputFile for PdfDocument creation
            // We'll use a temporary fileId that will be replaced after insert
            val tempFileId = UUID.randomUUID()
            val tempInputFile = InputFile(
                client = client,
                info = InputFileInfo(
                    fileId = tempFileId,
                    fileName = filename,
                    storageLocation = storageLocation,
                    uploadedAt = System.currentTimeMillis() / 1000,
                    numPages = pdfDocument.numberOfPages
                )
            )

            // Create PdfDocument and generate content hash
            val pdfDoc = PdfDocument(tempInputFile, pdfDocument)
            val contentHash = pdfDoc.contentHash()
            
            logger.info { "[${ctx.invocationId}] Generated content hash: $contentHash for file: $filename" }

            // Insert file record using InputFile object
            val fileId = fileService.insertFile(
                inputFile = tempInputFile,
                contentHash = contentHash,
                requestToken = eventId
            )
            
            logger.info { "[${ctx.invocationId}] Successfully inserted file: $filename for client: ${client.clientName} with ID: $fileId" }
        } catch (ex: Exception) {
            logger.error(ex) { "[${ctx.invocationId}] Error processing blob event: ${event.id}" }
            throw ex
        }
    }

    companion object {
        const val FUNCTION_NAME = "PutFileInfo"
    }
}

