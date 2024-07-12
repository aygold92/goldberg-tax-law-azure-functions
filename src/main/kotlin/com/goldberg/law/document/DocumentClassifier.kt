package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import javax.inject.Inject
import javax.inject.Named


class DocumentClassifier @Inject constructor(
    private val client: DocumentAnalysisClient,
    @Named("ClassifierModelId") private val modelId: String
) {

    fun classifyDocument(document: PdfDocument): List<ClassifiedPdfDocument> {
        val poller = retryWithBackoff(
            { client.beginClassifyDocument(modelId, document.toBinaryData()) },
            ::isAzureThrottlingError
        )

        // this should already have exponential backoff, but *shrug*
        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        println(result.toStringDetailed())

        return poller.finalResult.documents.map { resultDocument ->
            // I can get the pageNumber within the document using
            // document.pages?.get(resultDocument.boundingRegions[0].pageNumber)
            // however, I may need to check how many documents I get back if I send a multi-page document.
            // There might be multiple bounding regions?
            document.withType(resultDocument.docType)
        }
    }
}

