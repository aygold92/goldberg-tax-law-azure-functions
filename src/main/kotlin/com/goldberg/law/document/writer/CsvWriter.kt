package com.goldberg.law.document.writer

import com.goldberg.law.document.model.output.*
import com.goldberg.law.util.addQuotes

abstract class CsvWriter {

    fun writeRecordsToCsv(fileName: String, statements: List<BankStatement>) {
        val content = statements.sortedBy { it.statementDate }.joinToString("\n\n") { statement ->
            statement.getSuspiciousReasons().let { suspiciousReasons ->
                if (suspiciousReasons.isNotEmpty()) suspiciousReasons.toString().addQuotes() + "\n" + statement.toCsv()
                else statement.toCsv()
            }
        }
        storeCsv(fileName, TransactionHistoryRecord.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" + content)
    }

    fun writeAccountSummaryToCsv(fileName: String, accountSummaryEntries: List<AccountSummaryEntry>) {
        val content = AccountSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                accountSummaryEntries.joinToString("\n") { it.toCsv() }
        storeCsv(fileName, content)
    }

    fun writeStatementSummaryToCsv(fileName: String, statementSummaryEntries: List<StatementSummaryEntry>) {
        val content = StatementSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                statementSummaryEntries.joinToString("\n") { it.toCsv() }
        storeCsv(fileName, content)
    }

    fun writeCheckSummaryToCsv(fileName: String, checkEntries: Map<CheckDataKey, CheckData>, checksNotFound: Set<CheckDataKey>, checksNotUsed: Set<CheckDataKey>) {
        val content =
                listOf(CHECK_IMAGE_NOT_FOUND_HEADER, CHECK_IMAGE_NOT_USED_HEADER).joinToString(",") { it.addQuotes() } + "\n" +
                listOf(checksNotFound, checksNotUsed).joinToString(",") { it.toString().addQuotes() } + "\n\n" +
                
                CheckData.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                checkEntries.values.joinToString("\n") { it.toCsv() }
        storeCsv(fileName, content)
    }

    // TODO: what should content be?
    protected abstract fun storeCsv(fileName: String, content: String)

    companion object {
        const val CHECK_IMAGE_NOT_FOUND_HEADER = "Check Images Not Found"
        const val CHECK_IMAGE_NOT_USED_HEADER = "Check Images Not Used"
    }
}