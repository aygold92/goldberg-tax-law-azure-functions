package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.ClassifyDocumentOptions
import com.azure.ai.documentintelligence.models.SplitMode
import com.goldberg.law.document.model.pdf.*
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import javax.inject.Named


class DocumentClassifier @Inject constructor(
    private val client: DocumentIntelligenceClient,
    @Named("ClassifierModelId") private val modelId: String
) {
    private val logger = KotlinLogging.logger {}

    fun classifyDocument(document: PdfDocument): List<ClassifiedPdfDocument> {
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
            .filter { DocumentType.getBankType(it.second) != DocumentType.IRRELEVANT }

        var currentClassification: String? = null
        var currentPages = mutableSetOf<Int>()

        val ret = mutableListOf<ClassifiedPdfDocument>()

        pageClassifications.forEach {
            if (it.third.isStatementPage()) {
                if (currentPages.isNotEmpty() && currentClassification != null) {
                    ret.add(document.docForPages(currentPages).asClassifiedDocument(currentClassification!!))
                }
                // reset
                currentClassification = it.second
                currentPages = mutableSetOf(it.first)
            } else if (it.third.isTransactionPage() && currentClassification != null) {
                currentPages.add(it.first)
            } else if (it.third.isCheck()) {
                if (currentPages.isNotEmpty() && currentClassification != null) {
                    ret.add(document.docForPages(currentPages).asClassifiedDocument(currentClassification!!))
                }
                currentPages = mutableSetOf()
                currentClassification = null

                ret.add(document.docForPages(setOf( it.first)).asClassifiedDocument(it.second))
            } else {
                // skip
                logger.error { "Not adding $it to any classified document" }
            }
        }

        if (currentPages.isNotEmpty() && currentClassification != null) {
            ret.add(document.docForPages(currentPages).asClassifiedDocument(currentClassification!!))
        }

        return ret.also { logger.debug { it } }
    }
}

