package com.goldberg.law.document.model

import java.util.Date

data class BankStatement(
    val bankName: String,
    val statementDate: Date,
    val beginningBalance: Double,
    val endingBalance: Double,
    val totalPages: Int,
    val filename: String,
    val thPages: MutableList<TransactionHistoryPage> = mutableListOf()
) {
}