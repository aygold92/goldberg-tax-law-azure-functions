package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.core.util.Context
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
import com.goldberg.law.document.model.pdf.PdfDocumentMetadata
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.function.model.metadata.StatementMetadata
import com.goldberg.law.util.*
import com.google.common.collect.Sets
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.time.Duration

class AzureStorageDataManager(serviceClient: BlobServiceClient) {
    private val logger = KotlinLogging.logger {}

    private val inputContainerClient = serviceClient.getBlobContainerClient(INPUT_CONTAINER_NAME)
    private val splitInputContainerClient = serviceClient.getBlobContainerClient(SPLIT_INPUT_CONTAINER_NAME)
    private val modelsContainerClient = serviceClient.getBlobContainerClient(MODELS_CONTAINER_NAME)
    private val statementsContainerClient = serviceClient.getBlobContainerClient(STATEMENTS_CONTAINER_NAME)
    private val outputContainerClient = serviceClient.getBlobContainerClient(OUTPUT_CONTAINER_NAME)

    /**
     * Functions to save files
     */
    private fun saveFile(client: BlobContainerClient, fileName: String, content: BinaryData, overwrite: Boolean) {
        val blobClient = client.getBlobClient(fileName)
        blobClient.upload(content, overwrite)
        // Optionally, set the content type to indicate it's a CSV file
        // blobClient.setHttpHeaders(BlobHttpHeaders().setContentType("text/csv"))
    }

    private fun saveFile(client: BlobContainerClient, fileName: String, content: String, overwrite: Boolean): String {
        logger.debug { "Saving content to ${client.blobContainerName}/$fileName: $content" }
        saveFile(client, fileName, BinaryData.fromBytes(content.toByteArray(charset = Charsets.US_ASCII)), overwrite)
        return fileName
    }

    fun savePdfPage(document: PdfDocumentPage, overwrite: Boolean) {
        saveFile(splitInputContainerClient, document.splitPageFilePath(), document.toBinaryData(), overwrite)
    }

    fun saveModel(pdfDocumentPage: PdfPageData, dataModel: DocumentDataModel) =
        saveFile(modelsContainerClient, pdfDocumentPage.modelFileName(), dataModel.toStringDetailed(), true)

    fun saveBankStatement(bankStatement: BankStatement): String {
        val content = BinaryData.fromString(bankStatement.toStringDetailed())

        // TODO: build overwrite feature
//        val tagConditions = listOf(
//            "md5 = '${bankStatement.md5Hash()}",
//            "NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true')",
//        ).joinToString(" OR ")
        val tags = StatementMetadata(
            bankStatement.md5Hash(),
            bankStatement.isSuspicious(),
            bankStatement.getMissingChecks().isNotEmpty()
        )
        statementsContainerClient.getBlobClient(bankStatement.azureFileName()).uploadWithResponse(
            BlobParallelUploadOptions(content).apply {
                metadata = tags.toMap()
                setTags(tags.toMap())
//                setRequestConditions(BlobRequestConditions()
//                    .setTagsConditions("NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true') OR MissingChecks = 'true' OR md5 = '${bankStatement.md5Hash()}'"))
            },
            Duration.ofSeconds(5),
            Context.NONE
        )
        logger.debug { "Saved statement to ${statementsContainerClient.blobContainerName}/${bankStatement.azureFileName()}" }

        return bankStatement.azureFileName()
    }

    fun saveCsvOutput(fileName: String, content: String): String =
        saveFile(outputContainerClient, fileName, content, true)

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

    // loads a single PDF file into a collection of pages
    fun loadInputPdfDocumentPages(fileName: String): Collection<PdfDocumentPage> = loadFile(inputContainerClient, fileName).toBytes().let {
        val pdfDocument = loadPdfDocument(fileName, it)
        pdfDocument.pages.mapIndexed { idx, page -> PdfDocumentPage(fileName, pdfDocument.docForPage(idx), idx) }.also {
            logger.debug { "Successfully loaded file $fileName" }
        }
    }

    // loads a PDF Page document that has already been stored in the filesystem according to convention
    fun loadSplitPdfDocumentPage(pdfPageData: PdfPageData): PdfDocumentPage = loadFile(splitInputContainerClient, pdfPageData.splitPageFilePath()).toBytes().let {
        PdfDocumentPage(pdfPageData.fileName, loadPdfDocument(pdfPageData.fileName, it), pdfPageData.page).also {
            logger.debug { "Successfully loaded file $pdfPageData" }
        }
    }

    private fun loadPdfDocument(fileName: String, bytes: ByteArray) = try {
        Loader.loadPDF(bytes)
    } catch (ex: Exception) {
        throw InvalidPdfException("Unable to load PDF $fileName: $ex")
    }

    fun loadModel(pdfPageData: PdfPageData): DocumentDataModel {
        val modelBinaryData = loadFile(modelsContainerClient, pdfPageData.modelFileName())
        val tempModel = modelBinaryData.toObject(ExtraPageDataModel::class.java)
        return when {
            tempModel.isStatement() -> modelBinaryData.toObject(StatementDataModel::class.java)
            tempModel.isCheck() -> modelBinaryData.toObject(CheckDataModel::class.java)
            else -> tempModel
        }
    }

    fun loadBankStatement(bankStatementKey: String): BankStatement {
        val fileName = "${bankStatementKey}.json"
        return loadFile(statementsContainerClient, fileName).toObject(BankStatement::class.java)
    }

    /**
     * Functions to list files
     */
    // TODO: if there were 1000s of documents this would be very slow
    fun fetchInputPdfDocuments(requestedFileNames: Set<String>): Set<PdfDocumentMetadata> {
        val blobs = inputContainerClient.listBlobs(ListBlobsOptions().apply {
            details = BlobListDetails().apply { retrieveMetadata = true }
        }, Duration.ofSeconds(5)).toSet()
        val existingFiles = blobs.map { it.name }.toSet()
        val intersection = Sets.intersection(existingFiles, requestedFileNames)
        if (!intersection.containsAll(requestedFileNames)) {
            throw IllegalArgumentException("The following files do not exist: ${Sets.difference(requestedFileNames, intersection)}")
        }

        return blobs.filter { intersection.contains(it.name) }
            .map {
                val metadata = if (it.metadata != null) METADATA_OBJECT_MAPPER.convertValue(it.metadata, InputFileMetadata::class.java) else InputFileMetadata()
                PdfDocumentMetadata(it.name, metadata)
            }.toSet()
    }

    /**
     * Additional functions such as interacting with metadata
     */
    fun updateInputPdfMetadata(fileName: String, metadata: InputFileMetadata) {
        inputContainerClient.getBlobClient(fileName.withExtension(".pdf")).apply {
            setMetadata(metadata.toMap())
            tags = metadata.toMap()
        }
    }

    companion object {
        private val METADATA_OBJECT_MAPPER = OBJECT_MAPPER.copy().apply {
            val module = SimpleModule().apply {
                addDeserializer(Set::class.java, SetWithoutBracesDeserializer())
            }
            registerModule(module)
        }

        const val INPUT_CONTAINER_NAME = "input"
        const val SPLIT_INPUT_CONTAINER_NAME = "splitInput"
        const val MODELS_CONTAINER_NAME = "models"
        const val STATEMENTS_CONTAINER_NAME = "statements"
        const val OUTPUT_CONTAINER_NAME = "output"
    }
}