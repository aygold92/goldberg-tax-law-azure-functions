package com.goldberg.law.document.model

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import java.util.Date

// TODO: add reason for being suspicious
data class TransactionHistoryPage(
    val filename: String,
    val filePageNumber: Int,
    val statementDate: Date?,
    val statementPageNum: Int?,
    val statementTotalPages: Int?,
    val transactionHistoryRecords: List<TransactionHistoryRecord>
) {
    val isSuspicious: Boolean = transactionHistoryRecords.find { it.isSuspicious }?.isSuspicious == true ||
            listOf(statementDate, statementPageNum, statementTotalPages).contains(null)

    fun toCsv() = transactionHistoryRecords.joinToString("\n") { record ->
        listOf(record.toCsv(), metadataToCsv()).joinToString(",")
    }

    fun metadataToCsv() = listOf(filename.addQuotes(), filePageNumber, statementDate?.toTransactionDate(), statementPageNum, statementTotalPages)
        .joinToString(",") { it?.toString() ?: "" }

    companion object {
        const val STATEMENT_YEAR = "Statement Year"
        const val STATEMENT_DATE = "Statement Date"
        const val PAGE_NUM = "Page"
        const val TOTAL_PAGES = "Total Pages"
        const val TRANSACTION_TABLE = "Transaction Table Deposit/Withdrawal"
    }
}