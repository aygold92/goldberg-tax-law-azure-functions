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
}