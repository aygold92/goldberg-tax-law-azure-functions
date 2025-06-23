package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityInput
import com.goldberg.law.function.model.activity.ClassifyDocumentActivityOutput
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class ClassifyDocumentActivity(private val dataManager: AzureStorageDataManager, private val documentClassifier: DocumentClassifier) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun classifyDocument(
        @DurableActivityTrigger(name = "input") input: ClassifyDocumentActivityInput,
        context: ExecutionContext
    ): ClassifyDocumentActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val document = dataManager.loadInputPdfDocument(input.clientName, input.filename)
        val classifiedDocuments = documentClassifier.classifyDocument(document)
        val classifiedDocumentsMetadata = classifiedDocuments.map { it.toDocumentMetadata() }
        dataManager.saveClassificationData(input.clientName, input.filename, classifiedDocumentsMetadata)

        dataManager.updateInputPdfMetadata(input.clientName, input.filename, InputFileMetadata(numstatements = classifiedDocuments.size, classified = true))

        classifiedDocuments.forEach {
            dataManager.saveSplitPdf(input.clientName, it)
        }

        return ClassifyDocumentActivityOutput(input.filename, classifiedDocumentsMetadata).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "ClassifyDocument"
    }
}