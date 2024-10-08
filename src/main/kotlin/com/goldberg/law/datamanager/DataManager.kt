package com.goldberg.law.datamanager

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.output.*
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.function.model.InputFileMetadata
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toStringDetailed
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.Loader
import java.io.IOException

abstract class DataManager(protected val pdfSplitter: PdfSplitter) {
    protected val logger = KotlinLogging.logger {}

    fun writeRecordsToCsv(fileName: String, statements: List<BankStatement>): String {
        var lineCount = 0
        val content = statements.sortedBy { it.statementDate }.joinToString("\n\n") { statement ->
            // add 1 to lineCount to include the blank line between statements
            listOf(
                statement.suspiciousReasonsToCsv(),
                statement.toCsv(),
                statement.sumOfTransactionsToCsv(lineCount + 2)
            ).joinToString("\n")
                .also { lineCount += (if (statement.hasNoRecords()) 1 else statement.transactions.size) + 3 }
        }
        return saveCsvOutput("$fileName.csv", TransactionHistoryRecord.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" + content)
    }

    fun writeAccountSummaryToCsv(fileName: String, accountSummaryEntries: List<AccountSummaryEntry>): String {
        val content = AccountSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                accountSummaryEntries.joinToString("\n") { it.toCsv() }
        return saveCsvOutput("$fileName.csv", content)
    }

    fun writeStatementSummaryToCsv(fileName: String, statementSummaryEntries: List<StatementSummaryEntry>): String {
        val content = StatementSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                statementSummaryEntries.joinToString("\n") { it.toCsv() }
        return saveCsvOutput("$fileName.csv", content)
    }

    fun writeCheckSummaryToCsv(fileName: String, checkEntries: List<CheckDataModel>, checksNotFound: Set<CheckDataKey>, checksNotUsed: Set<CheckDataKey>): String {
        val content =
            listOf(CHECK_IMAGE_NOT_FOUND_HEADER, CHECK_IMAGE_NOT_USED_HEADER).joinToString(",") { it.addQuotes() } + "\n" +
                    listOf(checksNotFound, checksNotUsed).joinToString(",") { it.toString().addQuotes() } + "\n\n" +

                    CheckDataModel.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                    checkEntries.joinToString("\n") { it.toCsv() }
        return saveCsvOutput("$fileName.csv", content)
    }

    protected abstract fun saveCsvOutput(fileName: String, content: String): String

    abstract fun savePdfPage(document: PdfDocumentPage, overwrite: Boolean, outputDirectory: String? = null)

    fun saveModel(pdfDocumentPage: PdfDocumentPage, dataModel: DocumentDataModel, outputDirectory: String? = null) =
        saveModel(listOfNotNull(outputDirectory, pdfDocumentPage.modelFileName()).joinToString("/"), dataModel.toStringDetailed())

    abstract fun saveBankStatement(bankStatement: BankStatement, outputDirectory: String? = null): String

    protected abstract fun saveModel(fileName: String, content: String): String

    // loads a PDF Page document that has already been stored in the filesystem according to convention
    fun loadSplitPdfDocumentPage(pdfPageData: PdfPageData): PdfDocumentPage = loadSplitPdfDocumentFile(pdfPageData.splitPageFilePath()).let {
        PdfDocumentPage(pdfPageData.name, Loader.loadPDF(it), pdfPageData.page).also {
            logger.debug { "Successfully loaded file $pdfPageData" }
        }
    }

    abstract fun loadBankStatement(bankStatementKey: String, outputDirectory: String? = null): BankStatement

    // loads a single PDF file into a collection of pages
    fun loadInputPdfDocumentPages(fileName: String): Collection<PdfDocumentPage> = loadInputPdfDocumentPages(fileName, null)

    // loads a single PDF file into a collection of pages
    fun loadInputPdfDocumentPages(fileName: String, desiredPages: List<Int>?): Collection<PdfDocumentPage> = loadInputFile(fileName).let {
        pdfSplitter.splitPdf(fileName, loadPdfDocument(fileName, it), desiredPages)
    }.also { logger.debug { "Successfully loaded file $fileName, pages ${desiredPages ?: "[All]"}" } }

    private fun loadPdfDocument(fileName: String, bytes: ByteArray) = try {
        Loader.loadPDF(bytes)
    } catch (ex: Exception) {
        throw InvalidPdfException("Unable to load PDF $fileName: $ex")
    }

    protected abstract fun loadInputFile(fileName: String): ByteArray
    protected abstract fun loadSplitPdfDocumentFile(fileName: String): ByteArray

    // for a filesystem, finds the files in a directory or an individual file
    abstract fun findMatchingFiles(fileName: String): Set<String>

    // ensures requested files already exist
    abstract fun checkFilesExist(requestedFileNames: Set<String>): Set<String>

    abstract fun getProcessedFiles(fileNames: Set<String>): Set<String>

    abstract fun updateInputPdfMetadata(fileName: String, metadata: InputFileMetadata)

//    abstract fun fileExists(fileName: String): Boolean

    companion object {
        const val CHECK_IMAGE_NOT_FOUND_HEADER = "Check Images Not Found"
        const val CHECK_IMAGE_NOT_USED_HEADER = "Check Images Not Used"
    }
}