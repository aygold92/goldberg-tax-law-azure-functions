package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions
import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.CheckDataModel.Companion.toCheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.StatementDataModel.Companion.toBankDocument
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class DocumentDataExtractor @Inject constructor(
    @Inject private val client: DocumentIntelligenceClient,
    private val statementExtractorModelId: String,
    private val checkExtractorModelId: String,
) {
    private val logger = KotlinLogging.logger {}

    fun extractStatementData(classifiedDocument: ClassifiedPdfDocument): StatementDataModel {
        logger.info { "[Data Extractor] Processing $classifiedDocument" }
        return try {
            extractData(classifiedDocument, statementExtractorModelId).toBankDocument(classifiedDocument).also {
                logger.debug { "[Data Extractor]: Processed $classifiedDocument to ${it.toStringDetailed()}" }
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Exception processing $classifiedDocument: $ex" }
            StatementDataModel.blankModel(classifiedDocument)
        }
    }

    fun extractCheckData(classifiedDocument: ClassifiedPdfDocument): CheckDataModel {
        logger.info { "[Check Extractor] Processing file page $classifiedDocument" }
        return try {
            extractData(classifiedDocument, checkExtractorModelId).toCheckDataModel(classifiedDocument).also {
                logger.info { "[Check Extractor] Processed $classifiedDocument to ${it.toStringDetailed()}" }
            }
        } catch (e: Throwable) {
            // TODO: should I be hiding this error or failing?
            logger.error(e) { "Exception processing $classifiedDocument: $e" }
            CheckDataModel.blankModel(classifiedDocument)
        }
    }

    private fun extractData(pdfDocumentPage: PdfDocument, modelId: String): AnalyzedDocument {
        val options = AnalyzeDocumentOptions(pdfDocumentPage.toBinaryData())
        val poller = retryWithBackoff(
            { client.beginAnalyzeDocument(modelId, options) },
            ::isAzureThrottlingError
        )

        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)

        val documents = poller.finalResult.documents
        if (documents.size != 1) {
            logger.error { "${pdfDocumentPage.nameWithPages()} returned ${documents.size} analyzed documents" }
        }

        return documents[0].also { logger.trace { "Analyze API result: ${it.toStringDetailed()}" } }
    }
}