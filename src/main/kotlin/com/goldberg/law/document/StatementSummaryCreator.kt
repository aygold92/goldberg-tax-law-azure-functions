package com.goldberg.law.document

import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.util.toMonthYear
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class StatementSummaryCreator {
    private val logger = KotlinLogging.logger {}

    fun createSummary(statements: List<BankStatement>): List<StatementSummaryEntry> = try {
        val unusableStatements = statements.filter { it.accountNumber == null || it.statementDate == null }
        if (unusableStatements.isNotEmpty()) logger.warn { "Found statements without account number or date: $unusableStatements" }

        val usableStatements = statements.filter { it.accountNumber != null && it.statementDate != null }

        val summary: Map<String, MutableList<BankStatement>> = usableStatements.map { it.accountNumber!! }.toSet().associateWith { mutableListOf() }

        usableStatements.forEach {
            summary[it.accountNumber]!!.add(it)
        }

        summary.entries.mapNotNull { entry ->
            val sortedStatements = entry.value.filter { it.statementDate != null }.sortedBy { it.statementDate }
            if (sortedStatements.isEmpty()) {
                logger.warn {  }
                return@mapNotNull null
            }
            val first = sortedStatements.first().statementDate!!
            val last = sortedStatements.last().statementDate!!

            val calendar = Calendar.getInstance().apply {
                time = first
                set(Calendar.DAY_OF_MONTH, 1)  // since we're going up to the endMonth, make sure we're comparing using the first day of month
            }

            val allMonths = mutableListOf<String>()
            while (calendar.time.before(last) || calendar.time == last) {
                allMonths.add(calendar.time.toMonthYear())
                calendar.add(Calendar.MONTH, 1)
            }

            val existingMonths = sortedStatements.map { it.statementDate!!.toMonthYear() }

            val nonExistingMonths = allMonths.filter { !existingMonths.contains(it) }

            val suspiciousStatements = sortedStatements.filter { it.isSuspicious() }.map { it.statementDate!! }

            StatementSummaryEntry(entry.key, sortedStatements.first().classification, first, last, nonExistingMonths, suspiciousStatements)
        }

    } catch (ex: Exception) {
        logger.error(ex) { "Error creating summary: $ex" }
        listOf()
    }
}