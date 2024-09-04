package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toCurrency

data class StatementSummaryEntry(
    val accountNumber: String?,
    val classification: String?,
    val beginningBalance: Double?,
    val endingBalance: Double?,
    val netTransactions: Double?,
    val numTransactions: Int,
    val batesNumbers: Set<String>,
    val filename: String,
    val isSuspicious: Boolean,
    val suspiciousReasons: List<String>
) {
    fun toCsv(): String =
        listOf(
            accountNumber?.addQuotes(),
            classification?.addQuotes(),
            beginningBalance?.toCurrency(),
            endingBalance?.toCurrency(),
            netTransactions?.toCurrency(),
            numTransactions,
            batesNumbers.sorted().toString().addQuotes(),
            filename.addQuotes(),
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
            "Beginning Balance",
            "Ending Balance",
            "Net Transactions",
            "Number of Transactions",
            "Bates Stamps",
            "Filename",
            "Status",
            "Suspicious Reasons"
        )
    }
}