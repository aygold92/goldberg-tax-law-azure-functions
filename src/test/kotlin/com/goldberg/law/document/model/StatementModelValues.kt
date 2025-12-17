package com.goldberg.law.document.model

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.SummaryOfAccountsTable
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues.DEFAULT_ACCOUNT_NUMBER
import com.goldberg.law.entity.EntityValues.DEFAULT_AMOUNT
import com.goldberg.law.entity.EntityValues.DEFAULT_BATES_STAMP
import com.goldberg.law.entity.EntityValues.DEFAULT_CHECK_NUMBER
import com.goldberg.law.entity.EntityValues.DEFAULT_DATE
import com.goldberg.law.entity.EntityValues.DEFAULT_MEMO
import com.goldberg.law.entity.EntityValues.DEFAULT_PAYEE
import com.goldberg.law.entity.EntityValues.DEFAULT_STATEMENT_DATE_STRING
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassificationInfo
import com.goldberg.law.entity.EntityValues.newInputFile
import com.goldberg.law.entity.EntityValues.newInputFileInfo
import com.goldberg.law.entity.InputFile
import com.goldberg.law.util.asCurrency
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import java.math.BigDecimal

object StatementModelValues {
    const val REQUEST_ID = "RequestId"

    fun newClassificationWithPages(pages: Set<Int> = setOf(1)) =
        newClassification(
            inputFile = newInputFile(),
            info = newClassificationInfo(pages = pages),
        )

    fun newStatementModel(
        classification: Classification = newClassification(),
        date: String? = DEFAULT_STATEMENT_DATE_STRING,
        accountNumber: String? = DEFAULT_ACCOUNT_NUMBER,
        beginningBalance: BigDecimal? = 0.asCurrency(),
        endingBalance: BigDecimal? = 0.asCurrency(),
        feesCharged: BigDecimal? = null,
        interestCharged: BigDecimal? = null,
        summaryOfAccountsTable: SummaryOfAccountsTable? = null,
        transactionTableDepositWithdrawal: TransactionTableDepositWithdrawal? = null,
        transactionTableAmount: TransactionTableAmount? = null,
        transactionTableCreditsCharges: TransactionTableCreditsCharges? = null,
        transactionTableDebits: TransactionTableDebits? = null,
        transactionTableCredits: TransactionTableCredits? = null,
        transactionTableChecks: TransactionTableChecks? = null,
        batesStampsTable: BatesStampTable? = null,
    ) = StatementDataModel(
        documentType = "Test",
        classification = classification,
        date = date,
        accountNumber = accountNumber,
        beginningBalance = beginningBalance,
        endingBalance = endingBalance,
        feesCharged = feesCharged,
        interestCharged = interestCharged,
        summaryOfAccountsTable = summaryOfAccountsTable,
        transactionTableDepositWithdrawal = transactionTableDepositWithdrawal,
        transactionTableAmount = transactionTableAmount,
        transactionTableCreditsCharges = transactionTableCreditsCharges,
        transactionTableDebits = transactionTableDebits,
        transactionTableCredits = transactionTableCredits,
        transactionTableChecks = transactionTableChecks,
        batesStampsTable = batesStampsTable,
    )

    fun newCheckDataModel(
        accountNumber: String? = DEFAULT_ACCOUNT_NUMBER,
        checkNumber: Int? = DEFAULT_CHECK_NUMBER,
        to: String? = DEFAULT_PAYEE,
        description: String? = DEFAULT_MEMO,
        date: String? = DEFAULT_DATE,
        amount: BigDecimal? = DEFAULT_AMOUNT,
        batesStamp: String? = DEFAULT_BATES_STAMP,
        classification: Classification = newClassification(type = DocumentType.CheckTypes.CHECKS)
    ) = CheckDataModel(
        accountNumber,
        checkNumber,
        to,
        description,
        date,
        amount,
        null,
        batesStamp,
        classification
    )

    fun newCheckDataModel(accountNumber: String? = null, batesStamp: String? = DEFAULT_BATES_STAMP, classification: Classification = newClassification(), vararg checkEntries: CheckEntriesTableRow) = CheckDataModel(
        accountNumber,
        null,
        null,
        null,
        null,
        null,
        CheckEntriesTable(images = checkEntries.toList()),
        batesStamp,
        classification
    )

    fun newCheckEntriesTableRow(
        date: String? = DEFAULT_DATE,
        checkNumber: Int? = DEFAULT_CHECK_NUMBER,
        to: String? = DEFAULT_PAYEE,
        description: String? = DEFAULT_MEMO,
        amount: BigDecimal? = DEFAULT_AMOUNT,
        accountNumber: String? = DEFAULT_ACCOUNT_NUMBER,
        page: Int = 1,
    ) = CheckEntriesTableRow(
        date = date,
        checkNumber = checkNumber,
        to = to,
        description = description,
        amount = amount,
        accountNumber = accountNumber,
        page = page,
    )

    fun newBatesStampTable(numPages: Int) =
        BatesStampTable((1..numPages).map { BatesStampTableRow("$DEFAULT_BATES_STAMP-$it", it) })

    fun newBatesStampTable(vararg entries: Pair<Int, String>) =
        BatesStampTable((entries).map { BatesStampTableRow(it.second, it.first) })

    fun singleBatesStampsMap(page: Int) = mapOf(page to DEFAULT_BATES_STAMP)
    fun batesStampsMap(numPages: Int) = (1..numPages).associateWith { "$DEFAULT_BATES_STAMP-$it" }
    fun batesStampsMap(pages: Set<Int>) = pages.associateWith { "$DEFAULT_BATES_STAMP-$it" }

    fun newPdfDocument(pages: Set<Int> = setOf(1), file: InputFile = newInputFile(info = newInputFileInfo(numPages = pages.size))) = PdfDocument(
        file, PDDocument().apply { pages.forEach { addPage(PDPage()) } }
    )

    fun newClassifiedPdfDocument(pages: Set<Int> = setOf(1), classification: Classification = newClassification(inputFile = newInputFile(info = newInputFileInfo(numPages = pages.size)))) = ClassifiedPdfDocument(
        classification, PDDocument().apply { pages.forEach { addPage(PDPage()) } }
    )
}