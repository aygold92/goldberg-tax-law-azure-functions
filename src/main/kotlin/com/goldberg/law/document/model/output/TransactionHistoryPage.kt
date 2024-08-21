package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import java.util.Date

data class TransactionHistoryPage(
    val filename: String,
    val filePageNumber: Int,
    val batesStamp: String? = null, // ID number at the bottom of every submitted page, for tracking purposes
    val statementDate: Date? = null,
    val statementPageNum: Int? = null,
    val transactionHistoryRecords: List<TransactionHistoryRecord> = listOf()
) {
    fun toCsv(accountNumber: String?) = transactionHistoryRecords.sortedBy { it.date }.joinToString("\n") { record ->
        // include empty cell in between metadata and actual data
        listOf(record.toCsv(), "", metadataToCsv(accountNumber)).joinToString(",")
    }

    private fun metadataToCsv(accountNumber: String?) = listOf(
        accountNumber?.addQuotes(), batesStamp?.addQuotes(),
        statementDate?.toTransactionDate(), statementPageNum,
        filename.addQuotes(), filePageNumber
    ).joinToString(",") { it?.toString() ?: "" }

    /**
     * CALCULATING SUSPICIOUS REASONS
     */
    private val suspiciousReasonMap: List<Pair<() -> Boolean, String>> = listOf(
        Pair(::missingFields, SuspiciousReasons.MISSING_FIELDS.format(statementPageNum)),
        Pair(::hasSuspiciousRecords, SuspiciousReasons.HAS_SUSPICIOUS_RECORDS.format(statementPageNum)),
    )

    private fun missingFields() = listOf(statementDate, statementPageNum).contains(null)
    private fun getSuspiciousRecordReasons(): List<String> = transactionHistoryRecords.flatMap { it.isSuspiciousReasons }
    private fun hasSuspiciousRecords() = getSuspiciousRecordReasons().isNotEmpty()
    fun isSuspicious(): Boolean = getSuspiciousReasons().isNotEmpty()
    fun getSuspiciousReasons(): List<String> = suspiciousReasonMap.mapNotNull { if (it.first()) it.second else null }
        .plus(getSuspiciousRecordReasons())

    object SuspiciousReasons {
        const val MISSING_FIELDS = "Page %d is missing the StatementDate, Page Number, or Total Pages"
        const val HAS_SUSPICIOUS_RECORDS = "Page %d has suspicious records"
    }
}