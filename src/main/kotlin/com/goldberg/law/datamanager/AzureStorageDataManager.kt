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

    fun savePdfPage(clientName: String, document: PdfDocumentPage, overwrite: Boolean) {
        saveFile(getContainerClient(clientName, BlobContainer.SPLIT_INPUT), document.splitPageFilePath(), document.toBinaryData(), overwrite)
    }

    fun saveModel(clientName: String, pdfDocumentPage: PdfPageData, dataModel: DocumentDataModel) =
        saveFile(getContainerClient(clientName, BlobContainer.MODELS), pdfDocumentPage.modelFileName(), dataModel.toStringDetailed(), true)

    fun saveBankStatement(clientName: String, bankStatement: BankStatement): String {
        val content = BinaryData.fromString(bankStatement.toStringDetailed())

        // TODO: build overwrite feature
//        val tagConditions = listOf(
//            "md5 = '${bankStatement.md5Hash()}",
//            "NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true')",
//        ).joinToString(" OR ")
        val statementMetadata = StatementMetadata(
            bankStatement.md5Hash(),
            bankStatement.isSuspicious(),
            bankStatement.getMissingChecks().isNotEmpty(),
            false,
            bankStatement.statementType,
            bankStatement.getTotalSpending(),
            bankStatement.getTotalIncomeCredits(),
            bankStatement.transactions.size,
            bankStatement.filename,
            bankStatement.getPageRange()
        )
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

    // loads a single PDF file into a collection of pages
    fun loadInputPdfDocumentPages(clientName: String, fileName: String): Collection<PdfDocumentPage> = loadFile(getContainerClient(clientName, BlobContainer.INPUT), fileName).toBytes().let {
        val pdfDocument = loadPdfDocument(fileName, it)
        pdfDocument.pages.mapIndexed { idx, _ ->
            (idx + 1).let { pageNum -> PdfDocumentPage(fileName, pdfDocument.docForPage(pageNum), pageNum) }
        }.also { logger.debug { "Successfully loaded file $fileName" } }
    }

    // loads a PDF Page document that has already been stored in the filesystem according to convention
    fun loadSplitPdfDocumentPage(clientName: String, pdfPageData: PdfPageData): PdfDocumentPage = loadFile(getContainerClient(clientName, BlobContainer.SPLIT_INPUT), pdfPageData.splitPageFilePath()).toBytes().let {
        PdfDocumentPage(pdfPageData.fileName, loadPdfDocument(pdfPageData.fileName, it), pdfPageData.page).also {
            logger.debug { "Successfully loaded file $pdfPageData" }
        }
    }

    private fun loadPdfDocument(fileName: String, bytes: ByteArray) = try {
        Loader.loadPDF(bytes)
    } catch (ex: Exception) {
        throw InvalidPdfException("Unable to load PDF $fileName: $ex")
    }

    fun loadModel(clientName: String, pdfPageData: PdfPageData): DocumentDataModel {
        val modelBinaryData = loadFile(getContainerClient(clientName, BlobContainer.MODELS), pdfPageData.modelFileName())
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
    // TODO: if there were 1000s of documents this would be very slow
    fun fetchInputPdfDocuments(clientName: String, requestedFileNames: Set<String>): Set<PdfDocumentMetadata> {
        val blobs = getContainerClient(clientName, BlobContainer.INPUT).listBlobs(ListBlobsOptions().apply {
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
    fun updateInputPdfMetadata(clientName: String, fileName: String, metadata: InputFileMetadata) {
        getContainerClient(clientName, BlobContainer.INPUT).getBlobClient(fileName.withExtension(".pdf")).apply {
            setMetadata(metadata.toMap())
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
        const val SPLIT_INPUT_CONTAINER_NAME = "splitinput"
        const val MODELS_CONTAINER_NAME = "models"
        const val STATEMENTS_CONTAINER_NAME = "statements"
        const val OUTPUT_CONTAINER_NAME = "output"
    }
}