package com.goldberg.law.document.model.input.tables

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

abstract class TransactionTable(@JsonIgnore @Transient open val records: List<TransactionRecord>) {
    fun createTransactionDetails(date: String?, classification: Classification): List<TransactionDetails> = fromWrittenDate(date).let { statementDate ->
        records.map { it.toTransactionDetails(statementDate, classification) }
    }
}
abstract class TransactionRecord(open val id: UUID = UUID.randomUUID()) {
    abstract val page: Int

    @JsonIgnore @Transient
    val logger = KotlinLogging.logger {}
    abstract fun toTransactionDetails(statementDate: Date?, classification: Classification): TransactionDetails

    fun fromWrittenDateStatementDateOverride(monthDay: String?, statementDate: Date?): String? {
        val date = fromWrittenDate(monthDay, statementDate?.getYearSafe())
        return (if (date != null && date.getMonthInt() == 11 && statementDate?.getMonthInt() == 0 && date.getYearInt() != (statementDate.getYearInt() - 1)) {
            Calendar.getInstance().apply {
                time = date
                add(Calendar.YEAR, -1)
            }.time
        } else date)?.toTransactionDate()
    }

    fun extractCheckNumber(desc: String?): Int? {
        if (desc == null) return null
        val match = CHECK_REGEX.find(desc) ?: return null
        return match.groupValues[1].toInt()
    }

    companion object {
        private val CHECK_REGEX = Regex("^Check (\\d+)$") // Matches "Check xxx" where xxx is a number
    }
}
