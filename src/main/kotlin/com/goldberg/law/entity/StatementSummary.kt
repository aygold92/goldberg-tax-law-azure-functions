package com.goldberg.law.entity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class StatementSummary @JsonCreator constructor(
    @JsonProperty(CLASSIFICATION) val classification: Classification,
    @JsonProperty(STATEMENT_DETAILS) val statementDetails: StatementDetails,
    @JsonProperty(SUSPICIOUS_REASONS) val suspiciousReasons: List<String>,
    @JsonProperty(MISSING_CHECKS) val missingChecks: Set<String>,
    @JsonProperty(MANUALLY_VERIFIED) val manuallyVerified: Boolean = false,
    @JsonProperty(TOTAL_SPENDING) val totalSpending: BigDecimal,
    @JsonProperty(TOTAL_INCOME_CREDITS) val totalIncomeCredits: BigDecimal,
    @JsonProperty(NUM_TRANSACTIONS) val numTransactions: Int,
) {
    companion object {
        private const val CLASSIFICATION = "classification"
        private const val STATEMENT_DETAILS = "statementDetails"
        private const val SUSPICIOUS_REASONS = "suspiciousReasons"
        private const val MISSING_CHECKS = "missingChecks"
        private const val MANUALLY_VERIFIED = "manuallyVerified"
        private const val TOTAL_SPENDING = "totalSpending"
        private const val TOTAL_INCOME_CREDITS = "totalIncomeCredits"
        private const val NUM_TRANSACTIONS = "numTransactions"
    }
}