package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.ClassifyDocumentOptions
import com.azure.ai.documentintelligence.models.SplitMode
import com.goldberg.law.AppModule
import com.goldberg.law.document.model.pdf.*
import com.goldberg.law.entity.ClassifiedFile
import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import javax.inject.Named

class DocumentClassifier @Inject constructor(
    private val client: DocumentIntelligenceClient,
    @Named(AppModule.CLASSIFIER_MODEL_ID) private val modelId: String
) {
    private val logger = KotlinLogging.logger {}

    fun classifyDocument(document: PdfDocument): ClassifiedFile {
        val options = ClassifyDocumentOptions(document.toBinaryData())
            .setSplit(SplitMode.PER_PAGE)
        val poller = retryWithBackoff(
            { client.beginClassifyDocument(modelId, options) },
            ::isAzureThrottlingError
        )

        // this should already have exponential backoff, but *shrug*
        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        logger.debug { "Classify Operation Completed: ${result.toStringDetailed()}" }

        val pageClassifications = poller.finalResult.documents
            .mapIndexed { idx, doc -> Triple(idx + 1, doc.documentType, DocumentType.getBankType(doc.documentType)) }
            .filter { (_, _, documentType) -> documentType != DocumentType.EXTRA_PAGES }

        var currentClassification: String? = null
        var currentPages = mutableSetOf<Int>()

        val ret = mutableListOf<ClassifiedPages>()

        pageClassifications.forEach { (pageNum, classification, documentType) ->
            if (documentType.isStatementPage()) {
                if (currentPages.isNotEmpty() && currentClassification != null) {
                    ret.add(ClassifiedPages(currentPages, currentClassification))
                }
                // reset
                currentClassification = classification
                currentPages = mutableSetOf(pageNum)
            } else if (documentType.isTransactionPage() && currentClassification != null) {
                currentPages.add(pageNum)
            } else if (documentType.isCheck()) {
                ret.add(ClassifiedPages(setOf(pageNum), classification))
            } else {
                // skip
                logger.debug { "Not adding [$pageNum, $classification, $documentType] to any classified document" }
            }
        }

        if (currentPages.isNotEmpty() && currentClassification != null) {
            ret.add(ClassifiedPages(currentPages, currentClassification))
        }

        return ClassifiedFile(fileId = document.fileId, classifications = ret)
            .also { logger.debug { it } }
    }
}

