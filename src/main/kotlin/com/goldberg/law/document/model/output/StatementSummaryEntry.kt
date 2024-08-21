package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import java.util.Date

data class StatementSummaryEntry(
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
}