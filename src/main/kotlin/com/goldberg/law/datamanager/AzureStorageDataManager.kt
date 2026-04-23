package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.*
import com.azure.storage.blob.sas.BlobSasPermission
import com.azure.storage.blob.sas.BlobServiceSasSignatureValues
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.*
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.InputFile
import com.goldberg.law.function.api.model.SASTokenResult
import com.goldberg.law.util.*
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.time.OffsetDateTime
import java.util.UUID

class AzureStorageDataManager @Inject constructor(private val serviceClient: BlobServiceClient) {
    private val logger = KotlinLogging.logger {}

    /**
     * Functions to save files
     */
    private fun saveFile(storageLocation: StorageLocation, content: BinaryData, overwrite: Boolean): StorageLocation {
        try {
            serviceClient.getBlobContainerClient(storageLocation.containerName)
                .getBlobClient(storageLocation.filePathWithExt())
                .upload(content, overwrite)
            return storageLocation
        } catch (ex: Throwable) {
            logger.error { ex }
            throw ex
        }
        // Optionally, set the content type to indicate it's a CSV file
        // blobClient.setHttpHeaders(BlobHttpHeaders().setContentType("text/csv"))
    }

    private fun saveFile(storageLocation: StorageLocation, content: String, overwrite: Boolean): StorageLocation {
        logger.debug { "Saving content to $storageLocation: $content" }
        return saveFile(storageLocation, BinaryData.fromBytes(content.toByteArray(charset = Charsets.US_ASCII)), overwrite)
    }

    fun saveInputPdf(inputFile: InputFile, bytes: ByteArray) =
        saveFile(inputFile.storageLocation(), BinaryData.fromBytes(bytes), overwrite = false)

    fun saveSplitPdf(document: ClassifiedPdfDocument) =
        saveFile(document.classification.storageLocation(), document.toBinaryData(), true)

    fun saveModel(classification: Classification, dataModel: DocumentDataModel) =
        saveFile(classification.modelLocation(), dataModel.toStringDetailed(), true)

    /**
     * Functions to load files
     */
    private fun loadFile(storageLocation: StorageLocation): BinaryData = try {
        serviceClient.getBlobContainerClient(storageLocation.containerName).getBlobClient(storageLocation.filePathWithExt())
            .downloadContent()
    } catch (ex: BlobStorageException) {
        if (ex.errorCode == BlobErrorCode.BLOB_NOT_FOUND) {
            throw FileNotFoundException("File $storageLocation not found")
        }
        throw ex
    }

    /**
     * Load blob bytes by container name and blob path
     */
    fun loadUploadedPdfBytes(clientId: UUID, fileName: String): ByteArray =
        loadFile(uploadedFileLocation(clientId, fileName)).toBytes()

    fun loadInputPdfDocument(inputFile: InputFile): PdfDocument = inputFile.storageLocation().let { storageLocation ->
        val fileBytes = loadFile(storageLocation).toBytes()
        PdfDocument(inputFile, bytesToPDDocument(storageLocation, fileBytes))
    }

    // loads a PDF Page document that has already been stored in the filesystem according to convention
    fun loadSplitPdfDocument(classification: Classification): ClassifiedPdfDocument = classification.storageLocation().let { storageLocation ->
        val fileBytes = loadFile(storageLocation).toBytes()
        ClassifiedPdfDocument(classification, bytesToPDDocument(storageLocation, fileBytes))
            .also { logger.debug { "Successfully loaded file $storageLocation" } }
    }

    private fun bytesToPDDocument(storageLocation: StorageLocation, bytes: ByteArray) = try {
        Loader.loadPDF(bytes)
    } catch (ex: Exception) {
        throw InvalidPdfException("Unable to create PDF from bytes for $storageLocation: $ex")
    }

    fun loadModel(classification: Classification): DocumentDataModel {
        val json = loadFile(classification.modelLocation()).toString()
        return when (classification.documentType) {
            DocumentType.BANK, DocumentType.CREDIT_CARD -> OBJECT_MAPPER.readValue(json, StatementDataModel::class.java)
            DocumentType.CHECK -> OBJECT_MAPPER.readValue(json, CheckDataModel::class.java)
            else -> OBJECT_MAPPER.readValue(json, ExtraPageDataModel::class.java)
        }
    }

    /**
     * DELETION FUNCTIONS
     */
    private fun deleteFile(storageLocation: StorageLocation) {
        try {
            serviceClient.getBlobContainerClient(storageLocation.containerName).getBlobClient(storageLocation.filePathWithExt()).delete()
        } catch (ex: BlobStorageException) {
            logger.info { "File $storageLocation does not exist" }
        }
        logger.debug { "Deleted $storageLocation" }
    }

    fun deleteInputFile(file: InputFile) {
        deleteFile(file.storageLocation())
    }

    fun deleteSplitFile(classification: Classification) {
        deleteFile(classification.storageLocation())
    }

    fun deleteModelFile(classification: Classification) {
        deleteFile(classification.modelLocation())
    }

    fun doesFileExist(storageLocation: StorageLocation): Boolean = serviceClient.getBlobContainerClient(storageLocation.containerName)
        .getBlobClient(storageLocation.filePathWithExt())
        .exists()

    fun deleteUploadBlob(clientId: UUID, filename: String) =
        deleteFile(StorageLocation(clientId.toString(), "$UPLOAD_FOLDER/$filename", Extension.PDF))


    /**
     * Others
     */
    fun createClientContainerIfNotExists(containerName: UUID) {
        serviceClient.createBlobContainerIfNotExists(containerName.toString())
    }

    fun generateWriteSasToken(clientId: UUID, fileName: String): SASTokenResult {
        val location = uploadedFileLocation(clientId, fileName)
        return SASTokenResult(token = generateSasToken(location, BlobSasPermission().setWritePermission(true)), storageLocation = location)
    }

    fun generateReadSasToken(inputFile: InputFile): SASTokenResult {
        val location = inputFile.storageLocation()
        return SASTokenResult(token = generateSasToken(location, BlobSasPermission().setReadPermission(true)), storageLocation = location)
    }

    private fun generateSasToken(location: StorageLocation, permission: BlobSasPermission): String =
        serviceClient.getBlobContainerClient(location.containerName)
            .getBlobClient(location.filePathWithExt())
            .generateSas(BlobServiceSasSignatureValues(
                OffsetDateTime.now().plusMinutes(15),
                permission
            ))


    fun InputFile.storageLocation() = StorageLocation(clientId.toString(), "$INPUT_FILE_FOLDER/$fileId", Extension.PDF)
    fun Classification.storageLocation() = StorageLocation(clientId.toString(), "$SPLIT_INPUT_FILE_FOLDER/$classificationId", Extension.PDF)
    fun Classification.modelLocation() = StorageLocation(clientId.toString(), "$MODEL_FILE_FOLDER/$classificationId", Extension.JSON)

    companion object {
        const val UPLOAD_FOLDER = "uploads"
        const val INPUT_FILE_FOLDER = "input"
        const val SPLIT_INPUT_FILE_FOLDER = "splitInput"
        const val MODEL_FILE_FOLDER = "models"

        fun uploadedFileLocation(clientId: UUID, fileName: String) =
            StorageLocation(clientId.toString(), "$UPLOAD_FOLDER/$fileName", Extension.PDF)
    }
}
