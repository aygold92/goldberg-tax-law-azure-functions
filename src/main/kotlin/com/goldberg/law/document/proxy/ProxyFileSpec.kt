package com.goldberg.law.document.proxy

import java.time.LocalDate

data class ProxyFileSpec(
    val seed: String,
    val accountGroups: List<ProxyAccountGroup>
)

data class ProxyAccountGroup(
    val accountNumber: String,
    val startDate: LocalDate,
    val classificationSpecs: List<ProxyClassificationSpec>
)

sealed class ProxyClassificationSpec {
    data class StatementSpec(
        val isCreditCard: Boolean,
        val transactionCount: Int,
        val checkNumbers: List<Int>,
        val suspicious: Boolean
    ) : ProxyClassificationSpec()

    data class CheckPageSpec(
        val checkNumbers: List<Int>
    ) : ProxyClassificationSpec()
}
