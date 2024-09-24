package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toCurrency
import com.goldberg.law.util.toTransactionDate
import java.math.BigDecimal
import java.util.Date

data class StatementSummaryEntry(
    val accountNumber: String?,
    val classification: String?,
    val statementDate: Date?,
    val beginningBalance: BigDecimal?,
    val endingBalance: BigDecimal?,
    val netTransactions: BigDecimal?,
    val numTransactions: Int,
    val batesNumbers: Set<String>,
    val filename: String,
    val filePageNumbers: Set<Int>,
    val isSuspicious: Boolean,
    val suspiciousReasons: List<String>
) {
    fun toCsv(): String =
        listOf(
            accountNumber?.addQuotes(),
            classification?.addQuotes(),
            statementDate?.toTransactionDate(),
            beginningBalance?.toCurrency(),
            endingBalance?.toCurrency(),
            netTransactions?.toCurrency(),
            numTransactions,
            batesNumbers.sorted().toString().addQuotes(),
            filename.addQuotes(),
            filePageNumbers.sorted().toString().addQuotes(),
            if (isSuspicious) SUSPICIOUS else VERIFIED,
            if (suspiciousReasons.isNotEmpty()) suspiciousReasons.toString().addQuotes() else ""
        ).joinToString(",")

    companion object {
        const val SUSPICIOUS = "ERROR"
        const val VERIFIED = "Verified"

        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD */
        val CSV_FIELD_HEADERS = listOf(
            "Account",
            "Bank",
            "Statement Date",
            "Beginning Balance",
            "Ending Balance",
            "Net Transactions",
            "Number of Transactions",
            "Bates Stamps",
            "Filename",
            "File Page #s",
            "Status",
            "Suspicious Reasons"
        )
    }
}