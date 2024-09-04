package com.goldberg.law

import com.goldberg.law.document.*
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.CheckData
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.writer.CsvWriter
import com.goldberg.law.pdf.PdfSplitter
import com.goldberg.law.pdf.loader.PdfLoader
import com.goldberg.law.util.getFilename
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Guice
import com.google.inject.Injector
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject


class PdfExtractorMain @Inject constructor(
    private val pdfLoader: PdfLoader,
    private val splitter: PdfSplitter,
    private val classifier: DocumentClassifier,
    private val dataExtractor: DocumentDataExtractor,
    private val csvWriter: CsvWriter,
    private val accountNormalizer: AccountNormalizer,
    private val checkToStatementMatcher: CheckToStatementMatcher,
    private val accountSummaryCreator: AccountSummaryCreator
) {
    private val logger = KotlinLogging.logger {}

    fun run(req: AnalyzeDocumentRequest) {
        val inputDocuments = pdfLoader.loadPdfs(req.inputFile)

        val allStatements: MutableList<BankStatement> = mutableListOf()
        val allChecksMap: MutableMap<CheckDataKey, CheckData> = mutableMapOf()
        inputDocuments.forEach { inputDocument ->
            try {
                logger.info { "$HEADER_SECTION START DOCUMENT $inputDocument $HEADER_SECTION" }
                // current version of API can't classify one document into multiple pages (but the preview version can)
                val desiredPages = if (req.allPages) inputDocument.pages else req.getDesiredPages()
                val splitDocuments = splitter.splitPdf(inputDocument, desiredPages, true)

                val relevantDocuments = if (req.classifiedTypeOverride != null) {
                    logger.info { "Skipping classification as requested..." }
                    splitDocuments.map { it.withType(req.classifiedTypeOverride!!) }
                } else {
                    splitDocuments.map { classifier.classifyDocument(it) }
                }.filter { classifiedDoc -> classifiedDoc.isRelevant() }

                val transactionDocuments = relevantDocuments.filter { !it.isCheckDocument() }
                val checkDocuments = relevantDocuments.filter { it.isCheckDocument() }

                logger.debug { "Found relevant documents: $relevantDocuments" }

                if (transactionDocuments.isNotEmpty()) {
                    val statements = dataExtractor.extractTransactionHistory(transactionDocuments).sortedBy { it.statementDate }
                        .onEach { if (it.isSuspicious()) logger.error { "Statement ${it.primaryKey} is suspicious" } }
                    logger.debug { "=====Statements=====\n${statements.toStringDetailed()}" }
                    allStatements.addAll(statements)
                }

                if (checkDocuments.isNotEmpty()) {
                    val checksMap = dataExtractor.extractCheckData(checkDocuments)
                    allChecksMap.putAll(checksMap)
                    logger.debug { "=====Checks=====\n${checksMap.toStringDetailed()}" }
                }

                logger.info { "$HEADER_SECTION END DOCUMENT $inputDocument $HEADER_SECTION" }
            } catch (t: Throwable) {
                logger.error(t) { "Exception processing document $inputDocument: $t" }
            }
        }

        val (finalStatements, checksNotUsed, checksNotFound) = checkToStatementMatcher.matchChecksWithStatements(allStatements, allChecksMap)
        val summary = accountSummaryCreator.createSummary(finalStatements).also { logger.debug { it } }

        val filePrefix = "${req.outputDirectory}/${req.outputFile?.getFilename() ?: req.inputFile.getFilename()}"
        csvWriter.writeCheckSummaryToCsv("${filePrefix}_CheckSummary", allChecksMap, checksNotFound, checksNotUsed)
        csvWriter.writeAccountSummaryToCsv("${filePrefix}_AccountSummary", summary)
        csvWriter.writeStatementSummaryToCsv("${filePrefix}_StatementSummary", finalStatements.map { it.toStatementSummary() })
        csvWriter.writeRecordsToCsv("${filePrefix}_Records", finalStatements)
    }

    companion object {
        private const val HEADER_SECTION = "**********************************"
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