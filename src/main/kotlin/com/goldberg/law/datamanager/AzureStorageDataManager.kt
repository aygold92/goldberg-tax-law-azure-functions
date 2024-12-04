package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.core.util.Context
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.models.*
import com.azure.storage.blob.options.BlobParallelUploadOptions
import com.fasterxml.jackson.databind.exc.InvalidDefinitionException
import com.fasterxml.jackson.databind.module.SimpleModule
import com.goldberg.law.StorageBlobContainersNames
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.PdfDocumentMetadata
import com.goldberg.law.function.model.InputFileMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.*
import com.google.common.collect.Sets
import java.sql.Blob
import java.time.Duration

class AzureStorageDataManager(pdfSplitter: PdfSplitter, serviceClient: BlobServiceClient, containersNames: StorageBlobContainersNames): DataManager(pdfSplitter) {
    private val inputContainerClient = serviceClient.getBlobContainerClient(containersNames.input)
    private val splitInputContainerClient = serviceClient.getBlobContainerClient(containersNames.splitInput)
    private val modelsContainerClient = serviceClient.getBlobContainerClient(containersNames.models)
    private val outputContainerClient: BlobContainerClient = serviceClient.getBlobContainerClient(containersNames.output)

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

    override fun saveCsvOutput(fileName: String, content: String): String =
        saveFile(outputContainerClient, fileName, content, true)

    override fun saveModel(fileName: String, content: String): String =
        saveFile(modelsContainerClient, fileName, content, true)

    override fun saveBankStatement(bankStatement: BankStatement, outputDirectory: String?): String {
        val fileName = listOfNotNull(outputDirectory, bankStatement.azureFileName()).joinToString("/")
        val content = BinaryData.fromString(bankStatement.toStringDetailed())

        // TODO: build overwrite feature
//        val tagConditions = listOf(
//            "md5 = '${bankStatement.md5Hash()}",
//            "NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true')",
//        ).joinToString(" OR ")
        val tags = mapOf(
            SUSPICIOUS_BLOB_KEY to bankStatement.isSuspicious().toString(),
            CHECKS_MISSING_BLOB_KEY to bankStatement.getMissingChecks().isNotEmpty().toString(),
            MD5_BLOB_KEY to bankStatement.md5Hash(),
        )
        outputContainerClient.getBlobClient(fileName).uploadWithResponse(
            BlobParallelUploadOptions(content).apply {
                metadata = tags
                setTags(tags)
//                setRequestConditions(BlobRequestConditions()
//                    .setTagsConditions("NOT ($MANUALLY_VERIFIED_BLOB_KEY = 'true') OR MissingChecks = 'true' OR md5 = '${bankStatement.md5Hash()}'"))
            },
            Duration.ofSeconds(5),
            Context.NONE
        )

        return fileName
    }

    override fun savePdfPage(document: PdfDocumentPage, overwrite: Boolean, outputDirectory: String?) {
        val fileName = listOfNotNull(outputDirectory, document.splitPageFilePath()).joinToString("/")
        saveFile(splitInputContainerClient, fileName, document.toBinaryData(), overwrite)
    }

    private fun loadFile(client: BlobContainerClient, fileName: String): BinaryData = try {
        client.getBlobClient(fileName).downloadContent()
    } catch (ex: BlobStorageException) {
        if (ex.errorCode == BlobErrorCode.BLOB_NOT_FOUND) {
            throw FileNotFoundException("File $fileName not found in container ${client.blobContainerName}")
        }
        throw ex
    }

    override fun loadInputFile(fileName: String): ByteArray = loadFile(inputContainerClient, fileName).toBytes()
    override fun loadSplitPdfDocumentFile(fileName: String): ByteArray = loadFile(splitInputContainerClient, fileName).toBytes()
    override fun loadBankStatement(bankStatementKey: String, outputDirectory: String?): BankStatement {
        val fileName = listOfNotNull(outputDirectory, "${bankStatementKey}.json").joinToString("/")
        return loadFile(outputContainerClient, fileName).toObject(BankStatement::class.java)
    }

    override fun loadModel(pdfPageData: PdfPageData, outputDirectory: String?): DocumentDataModel {
        val modelBinaryData = loadFile(modelsContainerClient, pdfPageData.modelFileName())
        val tempModel = modelBinaryData.toObject(ExtraPageDataModel::class.java)
        return when {
            tempModel.isStatement() -> modelBinaryData.toObject(StatementDataModel::class.java)
            tempModel.isCheck() -> modelBinaryData.toObject(CheckDataModel::class.java)
            else -> tempModel
        }
    }

    override fun updateInputPdfMetadata(fileName: String, metadata: InputFileMetadata) {
        inputContainerClient.getBlobClient(fileName.withExtension(".pdf")).apply {
            setMetadata(metadata.toMap())
            tags = metadata.toMap()
        }
    }

    override fun findMatchingFiles(fileName: String): Set<String> = TODO("Not used")
//        inputContainerClient.listBlobs().mapPage {
//            if (it.name.startsWith(fileName) && it.isPdf()) it.name else null
//        }.filterNotNull().toSet().ifEmpty { throw InvalidArgumentException("No PDF files found for $fileName") }

    // TODO: if there were 1000s of documents this would be very slow
    override fun fetchInputPdfDocuments(requestedFileNames: Set<String>): Set<PdfDocumentMetadata> {
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

    override fun getProcessedFiles(fileNames: Set<String>): Set<String> {
        val documentNames = fileNames.map { it.getDocumentName() }
        return splitInputContainerClient.listBlobs().filter { it.getTopDirectory() in documentNames }.map { it.name }.toSet()
    }

    private fun BlobItem.getTopDirectory() = this.name.split("/")[0]

    companion object {
        const val SUSPICIOUS_BLOB_KEY = "Suspicious"
        const val CHECKS_MISSING_BLOB_KEY = "MissingChecks"
        const val MD5_BLOB_KEY = "md5"
        const val MANUALLY_VERIFIED_BLOB_KEY = "ManuallyVerified"
        val METADATA_OBJECT_MAPPER = OBJECT_MAPPER.copy().apply {
            val module = SimpleModule().apply {
                addDeserializer(Set::class.java, SetWithoutBracesDeserializer())
            }
            registerModule(module)
        }
    }
}