package com.goldberg.law.document.model

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.output.*
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocumentPage
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.util.*

object ModelValues {
    const val FILENAME = "test.pdf"
    const val BATES_STAMP = "AG-12345"
    const val ACCOUNT_NUMBER = "1234567890"
    const val CLIENT_NAME = "client"
    const val TEST_TRANSACTION_ID = "TestId"

    val FIXED_TRANSACTION_DATE = normalizeDate("4/3 2020")!!
    val FIXED_STATEMENT_DATE = normalizeDate("4/7 2020")!!
    val FIXED_STATEMENT_DATE_DATE = fromWrittenDate("4/7 2020")!!

    val BASIC_TH_PAGE_METADATA = TransactionHistoryPageMetadata(
        filePageNumber = 1,
        batesStamp = BATES_STAMP,
        statementPageNum = 2,
    )

    val BASIC_TH_RECORD = TransactionHistoryRecord(
        id = TEST_TRANSACTION_ID,
        date = FIXED_TRANSACTION_DATE,
        description = "test",
        amount = (-500).asCurrency(),
        pageMetadata = BASIC_TH_PAGE_METADATA
    )

    val BASIC_BANK_STATEMENT = newBankStatement().update(
        transactions = mutableListOf(BASIC_TH_RECORD),
        totalPages = 5,
        beginningBalance = 500.asCurrency(),
        endingBalance = ZERO,
        pageMetadata = BASIC_TH_PAGE_METADATA
    )
    const val CHECK_BATES_STAMP = "CH-12345"
    const val CHECK_FILENAME = "checkfile"
    const val CHECK_FILE_PAGE = 7
    val BASIC_CHECK_PAGE_METADATA = PdfDocumentPageMetadata(CHECK_FILENAME, CHECK_FILE_PAGE, DocumentType.CheckTypes.EAGLE_BANK_CHECK)

    fun newBankStatement(accountNumber: String? = ACCOUNT_NUMBER, statementDate: String? = FIXED_STATEMENT_DATE, classification: String = BankTypes.WF_BANK, filename: String = FILENAME) = BankStatement(
        filename = filename,
        classification = classification,
        date = normalizeDate(statementDate),
        accountNumber = accountNumber
    )

    fun newBasicBankStatement() = newBankStatement().update(
        transactions = mutableListOf(BASIC_TH_RECORD),
        totalPages = 5,
        beginningBalance = 500.asCurrency(),
        endingBalance = ZERO,
        pageMetadata = BASIC_TH_PAGE_METADATA
    )

    fun newHistoryRecord(date: String? = FIXED_TRANSACTION_DATE, checkNumber: Int? = null, description: String? = "test", amount: Double? = -500.0, pageMetadata: TransactionHistoryPageMetadata = BASIC_TH_PAGE_METADATA, checkData: CheckDataModel? = null, id: String = TEST_TRANSACTION_ID) = TransactionHistoryRecord(
        id = id,
        date = normalizeDate(date),
        checkNumber = checkNumber,
        description = description,
        amount = amount?.asCurrency(),
        pageMetadata = pageMetadata,
        checkDataModel = checkData
    )

    fun newTransactionPage(pageNum: Int, batesStamp: String? = BATES_STAMP) =
        TransactionHistoryPageMetadata(pageNum, batesStamp, pageNum)

    fun newCheckData(checkNumber: Int?, accountNumber: String? = ACCOUNT_NUMBER) = CheckDataModel(
        accountNumber,
        checkNumber,
        "Some Guy",
        "check desc",
        FIXED_TRANSACTION_DATE,
        (-500).asCurrency(),
        null,
        CHECK_BATES_STAMP,
        BASIC_CHECK_PAGE_METADATA
    )

    fun generateClassifiedPdfDocuments(numberOfDocs: Int, bankName: String): List<ClassifiedPdfDocumentPage> = IntRange(1, numberOfDocs).map {
        newClassifiedPdfDocument(FILENAME, it, bankName)
    }

    fun newClassifiedPdfDocument(filename: String = FILENAME, page: Int = 1, classification: String = BankTypes.WF_BANK) = ClassifiedPdfDocumentPage(
        filename, PDDocument().apply { addPage(PDPage()) }, page, classification
    )
}