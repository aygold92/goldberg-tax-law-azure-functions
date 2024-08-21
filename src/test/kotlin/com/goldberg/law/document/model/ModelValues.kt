package com.goldberg.law.document.model

import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.TransactionHistoryPage
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.pdf.model.StatementType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import java.util.*

object ModelValues {
    const val FILENAME = "test.pdf"
    const val BATES_STAMP = "AG-12345"
    const val ACCOUNT_NUMBER = "1234567890"

    val FIXED_TRANSACTION_DATE = fromWrittenDate("4/3", "2020")
    val FIXED_STATEMENT_DATE = fromWrittenDate("4/7", "2020")!!

    val BASIC_TH_RECORD = TransactionHistoryRecord(
        date = FIXED_TRANSACTION_DATE,
        description = "test",
        amount = -500.00
    )

    val BASIC_TH_PAGE = TransactionHistoryPage(
        filename = FILENAME,
        filePageNumber = 1,
        batesStamp = BATES_STAMP,
        statementDate = FIXED_STATEMENT_DATE,
        statementPageNum = 2,
        transactionHistoryRecords = listOf(BASIC_TH_RECORD)
    )

    val BASIC_BANK_STATEMENT = newBankStatement().update(
        thPage = BASIC_TH_PAGE,
        totalPages = 5,
        beginningBalance = 500.00,
        endingBalance = 1000.00
    )

    fun newBankStatement(accountNumber: String? = ACCOUNT_NUMBER, statementDate: Date? = FIXED_STATEMENT_DATE) = BankStatement(
        filename = FILENAME,
        classification = BankTypes.WF_BANK,
        statementDate = statementDate,
        accountNumber = accountNumber
    )

    fun newHistoryRecord(amount: Double = 0.0) = TransactionHistoryRecord(
        date = FIXED_TRANSACTION_DATE,
        description = "test",
        amount = amount
    )

    fun newTransactionPage(pageNum: Int, vararg amount: Double) = newTransactionPage(pageNum, *amount.map {
        newHistoryRecord(it)
    }.toTypedArray())


    fun newTransactionPage(pageNum: Int, vararg records: TransactionHistoryRecord) =
        TransactionHistoryPage(FILENAME, pageNum, BATES_STAMP, FIXED_STATEMENT_DATE, pageNum, records.toList())
}