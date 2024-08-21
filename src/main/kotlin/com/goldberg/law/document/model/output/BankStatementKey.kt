package com.goldberg.law.document.model.output

import com.goldberg.law.util.toTransactionDate
import java.util.Date

data class BankStatementKey(val statementDate: Date?, val accountNumber: String?) {
    override fun toString() = "${statementDate?.toTransactionDate()} - $accountNumber"

    fun isComplete(): Boolean = listOf(statementDate, accountNumber).all{ it != null }
}