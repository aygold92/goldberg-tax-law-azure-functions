package com.goldberg.law

import com.goldberg.law.document.AnalyzeDocumentRequest
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.StatementSummaryCreator
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.writer.CsvWriter
import com.goldberg.law.pdf.PdfSplitter
import com.goldberg.law.pdf.loader.PdfLoader
import com.goldberg.law.util.getFilename
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.withoutPdfExtension
import com.google.inject.Guice
import com.google.inject.Injector
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import kotlin.math.log


class PdfExtractorMain @Inject constructor(
    private val pdfLoader: PdfLoader,
    private val splitter: PdfSplitter,
    private val classifier: DocumentClassifier,
    private val dataExtractor: DocumentDataExtractor,
    private val csvWriter: CsvWriter,
    private val statementSummaryCreator: StatementSummaryCreator
) {
    private val logger = KotlinLogging.logger {}

    fun run(req: AnalyzeDocumentRequest) {
        val inputDocuments = pdfLoader.loadPdfs(req.inputFile)

        val allStatements: MutableList<BankStatement> = mutableListOf()
        inputDocuments.forEach { inputDocument ->
            try {
                // current version of API can't classify one document into multiple pages (but the preview version can)
                val desiredPages = if (req.allPages) inputDocument.pages else req.getDesiredPages()
                val splitDocuments = splitter.splitPdf(inputDocument, desiredPages, true)

                val classifiedDocuments = if (req.classifiedTypeOverride != null) {
                    logger.info { "Skipping classification as requested..." }
                    splitDocuments.map { it.withType(req.classifiedTypeOverride!!) }
                } else {
                    splitDocuments.map { classifier.classifyDocument(it) }
                }.filter { classifiedDoc -> classifiedDoc.isRelevant() }

                logger.debug { "Found relevant documents: $classifiedDocuments" }

                val statements =
                    dataExtractor.extractTransactionHistory(classifiedDocuments).sortedBy { it.statementDate }
                logger.debug { "=====Records=====\n${statements.toStringDetailed()}" }

                allStatements.addAll(statements)

                statements.filter { it.isSuspicious() }
                    .onEach { logger.error { "Statement ${it.primaryKey} is suspicious" } }

                val outputFilename = req.outputFile ?: inputDocument.name
                csvWriter.writeRecordsToCsv("${req.outputDirectory}/$outputFilename", statements)
            } catch (t: Throwable) {
                logger.error(t) { "Exception processing document $inputDocument: $t" }
            }
        }

        val summary = statementSummaryCreator.createSummary(allStatements)
        logger.debug { summary }
        csvWriter.writeSummaryToCsv("${req.outputDirectory}/${req.inputFile.getFilename()}_Summary", summary)
    }

    companion object {
        @JvmStatic
        fun main(args: Array<String>) {
            val request = AnalyzeDocumentRequest().parseArgs(args)
            val service = if (request.isProd) IntelligenceService.PROD else IntelligenceService.TEST
            val injector: Injector = Guice.createInjector(
                AppModule(AppEnvironmentSettings(service, ExecutionEnvironment.LOCAL))
            )
            injector.getInstance(PdfLoader::class.java)
            injector.getInstance(DocumentClassifier::class.java)
            injector.getInstance(PdfExtractorMain::class.java).run(request)
        }
    }
}