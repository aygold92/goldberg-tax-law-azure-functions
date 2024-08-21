package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import javax.inject.Named
import kotlin.math.log


class DocumentClassifier @Inject constructor(
    private val client: DocumentAnalysisClient,
    @Named("ClassifierModelId") private val modelId: String
) {
    private val logger = KotlinLogging.logger {}

    fun classifyDocument(document: PdfDocument): ClassifiedPdfDocument {
        val poller = retryWithBackoff(
            { client.beginClassifyDocument(modelId, document.toBinaryData()) },
            ::isAzureThrottlingError
        )

        // this should already have exponential backoff, but *shrug*
        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        logger.debug { "Classify Operation Completed: ${result.toStringDetailed()}" }

        if (poller.finalResult.documents.size != 1) {
            logger.error { "${document.nameWithPage} returned ${poller.finalResult.documents.size} classified pages" }
        }

        // If I want multi page documents, I can get the pageNumber within the document using
        // document.pages?.get(resultDocument.boundingRegions[0].pageNumber)
        // however, need to check how it works/how many documents I get back if I send a multi-page document.
        // There might be multiple bounding regions?
        return document.withType(poller.finalResult.documents[0].docType).also {
            logger.debug { it }
        }
    }
}

