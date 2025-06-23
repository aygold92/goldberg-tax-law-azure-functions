package com.goldberg.law.document.model

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.BatesStampTable
import com.goldberg.law.document.model.input.tables.BatesStampTableRow
import com.goldberg.law.document.model.output.*
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.math.BigDecimal

object ModelValues {
    const val REQUEST_ID = "RequestId"
    const val FILENAME = "test.pdf"
    const val BATES_STAMP = "AG-12345"
    const val ACCOUNT_NUMBER = "1234567890"
    const val CLIENT_NAME = "client"
    const val TEST_TRANSACTION_ID = "TestId"
    val DEFAULT_BATES_STAMP_MAP = singleBatesStampsMap(2)

    val FIXED_TRANSACTION_DATE = normalizeDate("4/3 2020")!!
    val FIXED_STATEMENT_DATE = normalizeDate("4/7 2020")!!
    val FIXED_STATEMENT_DATE_DATE = fromWrittenDate("4/7 2020")!!

    val BASIC_PDF_METADATA = ClassifiedPdfMetadata(
        filename = FILENAME,
        pages = setOf(2),
        classification = BankTypes.WF_BANK
    )

    val BASIC_TH_RECORD = TransactionHistoryRecord(
        id = TEST_TRANSACTION_ID,
        date = FIXED_TRANSACTION_DATE,
        description = "test",
        amount = (-500).asCurrency(),
        filePageNumber = 2
    )

    val BASIC_BANK_STATEMENT = BankStatement(
        pageMetadata = BASIC_PDF_METADATA,
        date = normalizeDate(FIXED_STATEMENT_DATE),
        accountNumber = ACCOUNT_NUMBER,
        beginningBalance = 500.asCurrency(),
        endingBalance = ZERO,
        transactions = mutableListOf(BASIC_TH_RECORD),
        batesStamps = batesStampsMap(setOf(2))
    )

    const val CHECK_BATES_STAMP = "CH-12345"
    const val CHECK_FILENAME = "checkfile"
    const val CHECK_FILE_PAGE = 7
    val BASIC_CHECK_PAGE_METADATA = ClassifiedPdfMetadata(CHECK_FILENAME, CHECK_FILE_PAGE, DocumentType.CheckTypes.EAGLE_BANK_CHECK)

    fun newBankStatement(
        filename: String = FILENAME,
        pages: Set<Int> = setOf(2),
        classification: String = BankTypes.WF_BANK,
        accountNumber: String? = ACCOUNT_NUMBER,
        beginningBalance: BigDecimal? = 500.asCurrency(),
        endingBalance: BigDecimal? = ZERO,
        interestCharged: BigDecimal? = null,
        feesCharged: BigDecimal? = null,
        statementDate: String? = FIXED_STATEMENT_DATE,
        transactions: List<TransactionHistoryRecord> = listOf(BASIC_TH_RECORD),
        batesStamps: Map<Int, String> = batesStampsMap(pages),
    ) = BankStatement(
        pageMetadata = ClassifiedPdfMetadata(
            filename = filename,
            pages = pages,
            classification = classification
        ),
        date = normalizeDate(statementDate),
        accountNumber = accountNumber,
        transactions = transactions,
        beginningBalance = beginningBalance,
        endingBalance = endingBalance,
        interestCharged = interestCharged,
        feesCharged = feesCharged,
        batesStamps = batesStamps,
    )

    fun newBasicBankStatement() = BASIC_BANK_STATEMENT.copy()

    fun newHistoryRecord(date: String? = FIXED_TRANSACTION_DATE, checkNumber: Int? = null, description: String? = "test", amount: Double? = -500.0, page: Int = 2, checkData: CheckDataModel? = null, id: String = TEST_TRANSACTION_ID) = TransactionHistoryRecord(
        id = id,
        date = normalizeDate(date),
        checkNumber = checkNumber,
        description = description,
        amount = amount?.asCurrency(),
        filePageNumber = page,
        checkDataModel = checkData
    )

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

    fun newPdfMetadata(filename: String = FILENAME,
                       pages: Set<Int> = setOf(2),
                       classification: String = BankTypes.WF_BANK) = ClassifiedPdfMetadata(
        filename = filename,
        pages = pages,
        classification = classification
    )

    fun newBatesStampTable(numPages: Int) =
        BatesStampTable((1..numPages).map { BatesStampTableRow("$BATES_STAMP-$it", it) })

    fun newBatesStampTable(vararg entries: Pair<Int, String>) =
        BatesStampTable((entries).map { BatesStampTableRow(it.second, it.first) })

    fun singleBatesStampsMap(page: Int) = mapOf(page to BATES_STAMP)
    fun batesStampsMap(numPages: Int) = (1..numPages).associateWith { "$BATES_STAMP-$it" }
    fun batesStampsMap(pages: Set<Int>) = pages.associateWith { "$BATES_STAMP-$it" }

    fun newPdfDocument(filename: String = FILENAME, page: Int = 1) = newClassifiedPdfDocumentMulti(filename, setOf(page))
    fun newPdfDocumentMulti(filename: String = FILENAME, pages: Set<Int> = setOf(1)) = PdfDocument(
        filename, PDDocument().apply { pages.forEach { addPage(PDPage()) } }, pages
    )

    fun newClassifiedPdfDocument(filename: String = FILENAME, page: Int = 1, classification: String = BankTypes.WF_BANK) = newClassifiedPdfDocumentMulti(filename, setOf(page), classification)
    fun newClassifiedPdfDocumentMulti(filename: String = FILENAME, pages: Set<Int> = setOf(1), classification: String = BankTypes.WF_BANK) = ClassifiedPdfDocument(
        filename, PDDocument().apply { pages.forEach { addPage(PDPage()) } }, pages, classification
    )




}