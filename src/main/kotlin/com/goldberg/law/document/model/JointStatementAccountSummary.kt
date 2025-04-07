package com.goldberg.law.document.model

import com.goldberg.law.document.model.input.SummaryOfAccountsTable
import java.util.*

data class JointStatementAccountSummary(
    val statementDate: Date,
    val classification: String,
    private val summaryOfAccountsTable: SummaryOfAccountsTable
) {
    fun findRelevantAccountIfMatches(statementDate: Date?, classification: String, statementPage: Int?): String? {
        return if (statementPage != null && this.statementDate == statementDate && this.classification == classification)
            summaryOfAccountsTable.findRelevantAccount(statementPage).accountNumber
        else null
    }

    // For NFCU bank hack
    fun getCombinedAccount(): String? {
        if (summaryOfAccountsTable.records.isEmpty()) return null
        return summaryOfAccountsTable.records.map { it.accountNumber }.joinToString(COMBINED_ACCOUNT_SEPARATOR)
    }

    companion object {
        const val COMBINED_ACCOUNT_SEPARATOR = "--"
    }
}