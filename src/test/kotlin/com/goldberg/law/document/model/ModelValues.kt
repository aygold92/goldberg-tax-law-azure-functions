package com.goldberg.law.document.model

import com.goldberg.law.document.model.output.*
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import java.util.*

object ModelValues {
    const val FILENAME = "test.pdf"
    const val BATES_STAMP = "AG-12345"
    const val ACCOUNT_NUMBER = "1234567890"

    val FIXED_TRANSACTION_DATE = fromWrittenDate("4/3", "2020")
    val FIXED_STATEMENT_DATE = fromWrittenDate("4/7", "2020")!!

    val BASIC_TH_PAGE_METADATA = TransactionHistoryPageMetadata(
        filename = FILENAME,
        filePageNumber = 1,
        batesStamp = BATES_STAMP,
        statementDate = FIXED_STATEMENT_DATE,
        statementPageNum = 2,
    )

    val BASIC_TH_RECORD = TransactionHistoryRecord(
        date = FIXED_TRANSACTION_DATE,
        description = "test",
        amount = -500.00,
        pageMetadata = BASIC_TH_PAGE_METADATA
    )

    val BASIC_BANK_STATEMENT = newBankStatement().update(
        transactions = mutableListOf(BASIC_TH_RECORD),
        totalPages = 5,
        beginningBalance = 500.00,
        endingBalance = 0.00
    )
    const val CHECK_BATES_STAMP = "CH-12345"
    const val CHECK_FILENAME = "checkfile"
    const val CHECK_FILE_PAGE = 7
    val BASIC_PAGE_METADATA = PageMetadata(CHECK_BATES_STAMP, CHECK_FILENAME, CHECK_FILE_PAGE)

    fun newBankStatement(accountNumber: String? = ACCOUNT_NUMBER, statementDate: Date? = FIXED_STATEMENT_DATE, classification: String = BankTypes.WF_BANK, filename: String = FILENAME) = BankStatement(
        filename = filename,
        classification = classification,
        statementDate = statementDate,
        accountNumber = accountNumber
    )

    fun newBasicBankStatement() = newBankStatement().update(
        transactions = mutableListOf(BASIC_TH_RECORD),
        totalPages = 5,
        beginningBalance = 500.00,
        endingBalance = 0.00
    )

    fun newHistoryRecord(date: Date? = FIXED_TRANSACTION_DATE, checkNumber: Int? = null, description: String = "test", amount: Double? = -500.0, pageMetadata: TransactionHistoryPageMetadata = BASIC_TH_PAGE_METADATA, checkData: CheckData? = null) = TransactionHistoryRecord(
        date = date,
        checkNumber = checkNumber,
        description = description,
        amount = amount,
        pageMetadata = pageMetadata,
        checkData = checkData
    )

    fun newTransactionPage(pageNum: Int, filename: String = FILENAME, batesStamp: String? = BATES_STAMP, statementDate: Date? = FIXED_STATEMENT_DATE) =
        TransactionHistoryPageMetadata(filename, pageNum, batesStamp, statementDate, pageNum)

    fun newCheckData(checkNumber: Int?, accountNumber: String? = ACCOUNT_NUMBER) = CheckData(
        accountNumber,
        checkNumber,
        "test",
        FIXED_TRANSACTION_DATE,
        -500.0,
        BASIC_PAGE_METADATA
    )
}