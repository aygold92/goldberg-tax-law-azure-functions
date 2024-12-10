package com.goldberg.law.document

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.output.*
import com.goldberg.law.util.addQuotes

class CsvCreator {
    fun recordsToCsv(statements: List<BankStatement>): String {
        var lineCount = 0
        val content = statements.sortedBy { it.statementDate }.joinToString("\n\n") { statement ->
            // add 1 to lineCount to include the blank line between statements
            listOf(
                statement.suspiciousReasonsToCsv(),
                statement.toCsv(),
                statement.sumOfTransactionsToCsv(lineCount + 2)
            ).joinToString("\n")
                .also { lineCount += (if (statement.hasNoRecords()) 1 else statement.transactions.size) + 3 }
        }
        return TransactionHistoryRecord.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" + content
    }

    fun accountSummaryToCsv(accountSummaryEntries: List<AccountSummaryEntry>): String =
        AccountSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                accountSummaryEntries.joinToString("\n") { it.toCsv() }

    fun statementSummaryToCsv(statementSummaryEntries: List<StatementSummaryEntry>): String =
        StatementSummaryEntry.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                statementSummaryEntries.joinToString("\n") { it.toCsv() }

    fun checkSummaryToCsv(checkEntries: List<CheckDataModel>, checksNotFound: Set<CheckDataKey>, checksNotUsed: Set<CheckDataKey>): String =
        listOf(CHECK_IMAGE_NOT_FOUND_HEADER, CHECK_IMAGE_NOT_USED_HEADER).joinToString(",") { it.addQuotes() } + "\n" +
                listOf(checksNotFound, checksNotUsed).joinToString(",") { it.toString().addQuotes() } + "\n\n" +

                CheckDataModel.CSV_FIELD_HEADERS.joinToString(",") { it.addQuotes() } + "\n" +
                checkEntries.joinToString("\n") { it.toCsv() }

    companion object {
        const val CHECK_IMAGE_NOT_FOUND_HEADER = "Check Images Not Found"
        const val CHECK_IMAGE_NOT_USED_HEADER = "Check Images Not Used"
    }
}