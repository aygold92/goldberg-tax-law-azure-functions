package com.goldberg.law.function.activity

import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.entity.Classification
import com.goldberg.law.function.activity.model.ClassifyDocumentActivityInput
import com.goldberg.law.function.activity.model.ClassifyDocumentActivityOutput
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Inject
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging

class ClassifyDocumentActivity @Inject constructor(
    private val classificationService: ClassificationService,
    private val azureStorageDataManager: AzureStorageDataManager,
    private val documentClassifier: DocumentClassifier
) {
    private val logger = KotlinLogging.logger {}

    @FunctionName(FUNCTION_NAME)
    fun classifyDocument(
        @DurableActivityTrigger(name = "input") input: ClassifyDocumentActivityInput,
        context: ExecutionContext
    ): ClassifyDocumentActivityOutput {
        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.toStringDetailed()}" }
        val document = azureStorageDataManager.loadInputPdfDocument(input.inputFile)
        val classifiedFile = documentClassifier.classifyDocument(document)
        val classificationInfos = classificationService.insertClassifications(classifiedFile)

        val classifications = classificationInfos.map { Classification(document.inputFile, it) }

        return ClassifyDocumentActivityOutput(input.inputFile.fileId, classifications).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning $it" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "ClassifyDocumentActivity"
    }
}