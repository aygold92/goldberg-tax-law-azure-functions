package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.AnalyzeDocumentOptions
import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.goldberg.law.AppModule
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.CheckDataModel.Companion.toCheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.StatementDataModel.Companion.toBankDocument
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.util.isAzureThrottlingError
import com.goldberg.law.util.retryWithBackoff
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Inject
import com.google.inject.name.Named
import io.github.oshai.kotlinlogging.KotlinLogging

class AzureDocumentDataExtractor @Inject constructor(
    private val client: DocumentIntelligenceClient,
    @Named(AppModule.STATEMENT_EXTRACTOR_MODEL_ID) private val statementExtractorModelId: String,
    @Named(AppModule.CHECK_EXTRACTOR_MODEL_ID) private val checkExtractorModelId: String,
) : DocumentDataExtractor() {
    private val logger = KotlinLogging.logger {}

    override fun extractStatementData(classifiedDocument: ClassifiedPdfDocument): StatementDataModel {
        logger.info { "[Data Extractor] Processing $classifiedDocument" }
        return try {
            extractData(classifiedDocument, statementExtractorModelId).toBankDocument(classifiedDocument.classification).also {
                logger.debug { "[Data Extractor]: Processed $classifiedDocument to ${it.toStringDetailed()}" }
            }
        } catch (ex: Exception) {
            logger.error(ex) { "Exception processing $classifiedDocument: $ex" }
            StatementDataModel.blankModel(classifiedDocument.classification)
        }
    }

    override fun extractCheckData(classifiedDocument: ClassifiedPdfDocument): CheckDataModel {
        logger.info { "[Check Extractor] Processing file page $classifiedDocument" }
        return try {
            extractData(classifiedDocument, checkExtractorModelId).toCheckDataModel(classifiedDocument.classification).also {
                logger.info { "[Check Extractor] Processed $classifiedDocument to ${it.toStringDetailed()}" }
            }
        } catch (e: Throwable) {
            // TODO: should I be hiding this error or failing?
            logger.error(e) { "Exception processing $classifiedDocument: $e" }
            CheckDataModel.blankModel(classifiedDocument.classification)
        }
    }

    private fun extractData(classifiedDocument: ClassifiedPdfDocument, modelId: String): AnalyzedDocument {
        val options = AnalyzeDocumentOptions(classifiedDocument.toBinaryData())
        val poller = retryWithBackoff(
            { client.beginAnalyzeDocument(modelId, options) },
            ::isAzureThrottlingError
        )

        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)

        val documents = poller.finalResult.documents
        if (documents.size != 1) {
            logger.error { "${classifiedDocument.classification.inputFile} returned ${documents.size} analyzed documents" }
        }

        return documents[0].also { logger.trace { "Analyze API result: ${it.toStringDetailed()}" } }
    }
}
