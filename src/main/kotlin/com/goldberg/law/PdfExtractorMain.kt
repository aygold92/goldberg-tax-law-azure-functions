package com.goldberg.law

import com.goldberg.law.document.AnalyzeDocumentRequest
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.writer.CsvWriter
import com.goldberg.law.pdf.loader.PdfLoader
import com.goldberg.law.pdf.PdfSplitter
import com.goldberg.law.util.toStringDetailed
import com.google.inject.Guice
import com.google.inject.Injector
import java.util.*
import javax.inject.Inject


class PdfExtractorMain @Inject constructor(
    private val pdfLoader: PdfLoader,
    private val splitter: PdfSplitter,
    private val classifier: DocumentClassifier,
    private val dataExtractor: DocumentDataExtractor,
    private val csvWriter: CsvWriter
    ) {

    fun run(req: AnalyzeDocumentRequest) {
        val inputDocuments = pdfLoader.loadPdfs(req.inputFile)

        val desiredPages = getDesiredPages(req)

        val splitDocuments = inputDocuments.flatMap {
            document -> splitter.splitPdf(document, desiredPages, true)
        }
        println()

        val thDocuments = splitDocuments.flatMap {
            classifier.classifyDocument(it)
        }.filter { it.isTransactionHistory() }

        println("Found transaction history documents: $thDocuments")

        val thPages = thDocuments.flatMap { dataExtractor.extractTransactionHistory(it) }
        println("=====Records=====")
        println(thPages.toStringDetailed())

        val suspiciousPages = thPages.filter { it.isSuspicious }
        val verifiedPages = thPages.filter { !it.isSuspicious }

        csvWriter.writeRecordsToCsv("${req.outputDirectory}/${req.outputFile}", verifiedPages)
        // TODO: can I add a record with the file metadata in between each one?  Also, where do I actually output the reasons to the customer?
        csvWriter.writeRecordsToCsv("${req.outputDirectory}/[SUSPICIOUS]${req.outputFile}", suspiciousPages)
    }

    private fun getDesiredPages(req: AnalyzeDocumentRequest): List<Int> {
        val range: List<Int> = req.range?.first?.rangeTo(req.range!!.second)?.toList()
            .let { it ?: Collections.emptyList() }

        val pages: List<Int> = req.pages?.toList()
            .let { it ?: Collections.emptyList() }

        return (range + pages).distinct().sorted()
    }

    private fun validateArgs(request: AnalyzeDocumentRequest) {

    }

    companion object {
        @JvmStatic fun main(args: Array<String>) {
            val injector: Injector = Guice.createInjector(AppModule(
                AppEnvironmentSettings(IntelligenceService.TEST, ExecutionEnvironment.LOCAL))
            )
            val request = AnalyzeDocumentRequest().parseArgs(args)
            injector.getInstance(PdfLoader::class.java)
            injector.getInstance(DocumentClassifier::class.java)
            injector.getInstance(PdfExtractorMain::class.java).run(request)
        }
    }
}