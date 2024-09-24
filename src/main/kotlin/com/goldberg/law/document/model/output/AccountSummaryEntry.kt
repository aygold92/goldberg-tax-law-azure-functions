package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import java.util.Date

data class AccountSummaryEntry @JsonCreator constructor(
    val accountNumber: String,
    val bankType: String,
    val first: Date,
    val last: Date,
    val missing: List<String>,
    val suspicious: List<Date>
) {
    fun toCsv(): String = listOf(
        accountNumber.addQuotes(), bankType, first.toTransactionDate(), last.toTransactionDate(),
        missing.toString().addQuotes(),
        suspicious.map { it.toTransactionDate() }.toString().addQuotes()).joinToString(",")

    companion object {
        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD */
        val CSV_FIELD_HEADERS = listOf(
            "Account",
            "Bank",
            "First Statement",
            "Last Statement",
            "Missing Statements",
            "Suspicious Statements"
        )
    }
}