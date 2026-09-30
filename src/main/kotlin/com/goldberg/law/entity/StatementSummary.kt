package com.goldberg.law.entity

import java.math.BigDecimal

data class StatementSummary(
    val classification: Classification,
    val statementDetails: StatementDetails,
    val suspiciousReasons: List<String>,
    val missingChecks: Set<String>,
    val manuallyVerified: Boolean = false,
    val totalSpending: BigDecimal,
    val totalIncomeCredits: BigDecimal,
    val numTransactions: Int,
    /** Transactions awaiting review, plus one if the statement's own flags are awaiting review. */
    val pendingReviewCount: Int = 0,
)