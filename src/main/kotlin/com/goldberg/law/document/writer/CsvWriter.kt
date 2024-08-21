package com.goldberg.law.document.writer

import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.util.addQuotes

abstract class CsvWriter {

    fun writeRecordsToCsv(fileName: String, statements: List<BankStatement>) {
        val content = statements.sortedBy { it.statementDate }.joinToString("\n\n") { statement ->
            statement.getSuspiciousReasons().let { suspiciousReasons ->
                if (suspiciousReasons.isNotEmpty()) suspiciousReasons.toString() + "\n" + statement.toCsv()
                else statement.toCsv()
            }
        }
        storeCsv(fileName, fieldHeaders.joinToString { it.addQuotes() } + "\n" + content)
    }

    fun writeSummaryToCsv(fileName: String, statementSummaryEntries: List<StatementSummaryEntry>) {
        val content = fieldHeadersForSummary.joinToString { it.addQuotes() } +
                "\n" +
                statementSummaryEntries.joinToString("\n") { it.toCsv() }
        storeCsv(fileName, content)
    }

    // TODO: what should content be?
    protected abstract fun storeCsv(fileName: String, content: String)

    companion object {
        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD of
         * [com.goldberg.law.document.model.output.TransactionHistoryPage] and
         * [com.goldberg.law.document.model.output.TransactionHistoryRecord]
         * */
        val fieldHeaders = listOf(
            "Transaction Date",
            "Description",
            "Amount",
            "",
            "Account",
            "Bates Stamp",
            "Statement Date",
            "Statement Page #",
            "Filename",
            "File Page #"
        )

        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD of
         * [com.goldberg.law.document.model.output.StatementSummaryEntry]
         * */
        val fieldHeadersForSummary = listOf(
            "Account",
            "Bank",
            "First Statement",
            "Last Statement",
            "Missing Statements",
            "Suspicious Statements"
        )
    }
}