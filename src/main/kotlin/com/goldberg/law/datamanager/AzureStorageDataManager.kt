package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.core.util.Context
import com.azure.core.util.serializer.TypeReference
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.*
import com.azure.storage.blob.options.BlobParallelUploadOptions
import com.fasterxml.jackson.databind.module.SimpleModule
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.pdf.*
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.metadata.StatementMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.*
import com.google.common.collect.Sets
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.time.Duration

class AzureStorageDataManager(private val serviceClient: BlobServiceClient) {
    private val logger = KotlinLogging.logger {}

    private val clients: MutableMap<String, BlobContainerClient> = mutableMapOf()

    private fun getContainerClient(clientName: String, containerName: BlobContainer, ): BlobContainerClient {
        val containerClientName = containerName.forClient(clientName)
        if (clients.containsKey(containerClientName)) {
            return clients[containerClientName]!!
        } else {
            clients[containerClientName] = serviceClient.getBlobContainerClient(containerClientName)
            return clients[containerClientName]!!
        }
    }

    /**
     * Functions to save files
     */
    private fun saveFile(client: BlobContainerClient, fileName: String, content: BinaryData, overwrite: Boolean) {
        val blobClient = client.getBlobClient(fileName)
        try {
            blobClient.upload(content, overwrite)
        } catch (ex: Throwable) {
            logger.error { ex }
            throw ex
        }
        // Optionally, set the content type to indicate it's a CSV file
        // blobClient.setHttpHeaders(BlobHttpHeaders().setContentType("text/csv"))
    }

    private fun saveFile(client: BlobContainerClient, fileName: String, content: String, overwrite: Boolean): String {
        logger.debug { "Saving content to ${client.blobContainerName}/$fileName: $content" }
        saveFile(client, fileName, BinaryData.fromBytes(content.toByteArray(charset = Charsets.US_ASCII)), overwrite)
        return fileName
    }

    fun saveClassificationData(clientName: String, filename: String, classifiedData: List<ClassifiedPdfMetadata>) {
        saveFile(getContainerClient(clientName, BlobContainer.CLASSIFICATIONS), filename.getDocumentName(), classifiedData.toStringDetailed(), true)
    }

    fun saveSplitPdf(clientName: String, document: PdfDocument) {
        // TODO: should I add the classification in metadata?
        saveFile(getContainerClient(clientName, BlobContainer.SPLIT_INPUT), document.splitPageFilePath(), document.toBinaryData(), true)
    }

    fun saveModel(clientName: String, dataModel: DocumentDataModel) =
        saveFile(getContainerClient(clientName, BlobContainer.MODELS), dataModel.pageMetadata.modelFileName(), dataModel.toStringDetailed(), true)

    fun saveBankStatement(clientName: String, bankStatement: BankStatement, manuallyVerified: Boolean = false): String {
        val content = BinaryData.fromString(bankStatement.toStringDetailed())

        // TODO: build overwrite feature
//        val tagConditions = listOf(
//            "md5 = '${bankStatement.md5Hash()}",
//            "NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true')",
//        ).joinToString(" OR ")
        val statementMetadata = bankStatement.getStatementMetadata(manuallyVerified)
        val containerClient = getContainerClient(clientName, BlobContainer.STATEMENTS)
        containerClient.getBlobClient(bankStatement.azureFileName()).uploadWithResponse(
            BlobParallelUploadOptions(content).apply {
                metadata = statementMetadata.toMetadataMap()
                tags = statementMetadata.toTagsMap()
//                setRequestConditions(BlobRequestConditions()
//                    .setTagsConditions("NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true') OR MissingChecks = 'true' OR md5 = '${bankStatement.md5Hash()}'"))
            },
            Duration.ofSeconds(5),
            Context.NONE
        )
        logger.debug { "Saved statement to ${containerClient.blobContainerName}/${bankStatement.azureFileName()} with metadata ${statementMetadata.toMetadataMap()}" }

        return bankStatement.azureFileName()
    }

    fun saveCsvOutput(clientName: String, fileName: String, content: String): String =
        saveFile(getContainerClient(clientName, BlobContainer.OUTPUT), fileName, content, true)

    /**
     * Functions to load files
     */
    private fun loadFile(client: BlobContainerClient, fileName: String): BinaryData = try {
        client.getBlobClient(fileName).downloadContent()
    } catch (ex: BlobStorageException) {
        if (ex.errorCode == BlobErrorCode.BLOB_NOT_FOUND) {
            throw FileNotFoundException("File $fileName not found in container ${client.blobContainerName}")
        }
        throw ex
    }

    fun loadInputPdfDocument(clientName: String, fileName: String): PdfDocument = loadFile(getContainerClient(clientName, BlobContainer.INPUT), fileName).toBytes().let {
        val pdfDocument = loadPdfDocument(fileName, it)
        return PdfDocument(fileName, pdfDocument)
    }

    fun loadDocumentClassification(clientName: String, filename: String): List<ClassifiedPdfMetadata> =
        loadFile(getContainerClient(clientName, BlobContainer.CLASSIFICATIONS), filename.getDocumentName())
            .toObject(object: TypeReference<List<ClassifiedPdfMetadata>>(){})

    // loads a PDF Page document that has already been stored in the filesystem according to convention
    fun loadSplitPdfDocument(clientName: String, pdfMetadata: ClassifiedPdfMetadata): ClassifiedPdfDocument =
        loadFile(getContainerClient(clientName, BlobContainer.SPLIT_INPUT), pdfMetadata.splitPageFilePath()).toBytes().let {
            ClassifiedPdfDocument(pdfMetadata.filename, loadPdfDocument(pdfMetadata.filename, it), pdfMetadata.pages, pdfMetadata.classification)
                .also { logger.debug { "Successfully loaded file $pdfMetadata" } }
        }

    private fun loadPdfDocument(fileName: String, bytes: ByteArray) = try {
        Loader.loadPDF(bytes)
    } catch (ex: Exception) {
        throw InvalidPdfException("Unable to load PDF $fileName: $ex")
    }

    fun loadModel(clientName: String, pdfMetadata: IPdfMetadata): DocumentDataModel {
        val modelBinaryData = loadFile(getContainerClient(clientName, BlobContainer.MODELS), pdfMetadata.modelFileName())
        val tempModel = modelBinaryData.toObject(ExtraPageDataModel::class.java)
        return when {
            tempModel.isStatement() -> modelBinaryData.toObject(StatementDataModel::class.java)
            tempModel.isCheck() -> modelBinaryData.toObject(CheckDataModel::class.java)
            else -> tempModel
        }
    }

    fun loadBankStatement(clientName: String, bankStatementKey: String): BankStatement {
        val fileName = "${bankStatementKey}.json"
        return loadFile(getContainerClient(clientName, BlobContainer.STATEMENTS), fileName).toObject(BankStatement::class.java)
    }

    /**
     * Functions to list files
     */
    /**
     * List all input PDF documents for a client and return a map from filename to InputFileMetadata.
     */
    fun listInputPdfDocuments(clientName: String): Map<String, InputFileMetadata> {
        val blobs = getContainerClient(clientName, BlobContainer.INPUT).listBlobs(
            ListBlobsOptions().apply { details = BlobListDetails().apply { retrieveMetadata = true } },
            Duration.ofSeconds(5)
        ).toSet()
        return blobs.associate {
            val metadata = if (it.metadata != null) METADATA_OBJECT_MAPPER.convertValue(it.metadata, InputFileMetadata::class.java) else InputFileMetadata()
            it.name to metadata
        }
    }

    /**
     * List all statement files for a client and return a map from filename to StatementMetadata.
     */
    fun listStatementsWithMetadata(clientName: String): Map<String, StatementMetadata> {
        val containerClient = getContainerClient(clientName, BlobContainer.STATEMENTS)
        val blobs = containerClient.listBlobs(
            ListBlobsOptions().apply { details = BlobListDetails().apply { retrieveMetadata = true } },
            Duration.ofSeconds(5)
        )
        val result = mutableMapOf<String, StatementMetadata>()
        for (blob in blobs) {
            val metadataMap = blob.metadata?.mapKeys { it.key.lowercase() } ?: continue
            try {
                val statementMetadata = StatementMetadata.fromMetadataMap(metadataMap, blob.name)
                result[blob.name] = statementMetadata
            } catch (ex: Exception) {
                logger.warn { "Failed to parse StatementMetadata for ${blob.name}: $ex" }
            }
        }
        return result
    }

    /**
     * DELETION FUNCTIONS
     */
    private fun deleteFile(containerClient: BlobContainerClient, filename: String) {
        try {
            containerClient.getBlobClient(filename).delete()
        } catch (ex: BlobStorageException) {
            logger.info { "File $filename does not exist" }
        }
        logger.debug { "Deleted ${containerClient.blobContainerName}/${filename}" }
    }

    fun deleteInputFile(clientName: String, filename: String) {
        deleteFile(getContainerClient(clientName, BlobContainer.INPUT), filename)
    }

    fun deleteSplitFile(clientName: String, pdfMetadata: ClassifiedPdfMetadata) {
        deleteFile(getContainerClient(clientName, BlobContainer.SPLIT_INPUT), pdfMetadata.splitPageFilePath())
    }

    fun deleteModelFile(clientName: String, pdfMetadata: ClassifiedPdfMetadata) {
        deleteFile(getContainerClient(clientName, BlobContainer.MODELS), pdfMetadata.modelFileName())
    }

    fun deleteBankStatement(clientName: String, statementKey: String) {
        // ensure statementKey ends with .json, if not, add it
        val finalStatementKey = if (statementKey.endsWith(".json")) statementKey else "${statementKey}.json"
        deleteFile(getContainerClient(clientName, BlobContainer.STATEMENTS), finalStatementKey)
    }

    /**
     * Additional functions such as interacting with metadata
     */
    fun updateInputPdfMetadata(clientName: String, fileName: String, metadata: InputFileMetadata) {
        getContainerClient(clientName, BlobContainer.INPUT).getBlobClient(fileName.withExtension(".pdf")).apply {
            setMetadata(metadata.toMap())
        }
    }

    fun loadInputMetadata(clientName: String, fileName: String): InputFileMetadata? {
        val metadataMap = getContainerClient(clientName, BlobContainer.INPUT).getBlobClient(fileName.withExtension(".pdf")).properties.metadata
        return if (metadataMap == null) null else METADATA_OBJECT_MAPPER.convertValue(metadataMap, InputFileMetadata::class.java)
    }

    companion object {
        private val METADATA_OBJECT_MAPPER = OBJECT_MAPPER.copy().apply {
            val module = SimpleModule().apply {
                addDeserializer(Set::class.java, SetWithoutBracesDeserializer())
            }
            registerModule(module)
        }
    }
}