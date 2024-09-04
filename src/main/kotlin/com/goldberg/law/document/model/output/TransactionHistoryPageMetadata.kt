package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import java.util.Date

data class TransactionHistoryPageMetadata(
    val filename: String,
    val filePageNumber: Int,
    val batesStamp: String? = null, // ID number at the bottom of every submitted page, for tracking purposes
    val statementDate: Date? = null,
    val statementPageNum: Int? = null,
) {
    fun toCsv(accountNumber: String?, classification: String) = listOf(
        "$classification - ${accountNumber ?: UNKNOWN_ACCOUNT}".addQuotes(), batesStamp?.addQuotes(),
        statementDate?.toTransactionDate(), statementPageNum,
        filename.addQuotes(), filePageNumber
    ).joinToString(",") { it?.toString() ?: "" }

    companion object {
        const val UNKNOWN_ACCOUNT = "Unknown"
    }
}