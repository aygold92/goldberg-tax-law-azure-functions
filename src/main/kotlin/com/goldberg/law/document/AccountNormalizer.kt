package com.goldberg.law.document

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * TODO: normalize to last 4
 */
class AccountNormalizer {
    private val logger = KotlinLogging.logger {}

    fun normalizeAccounts(allStatementModels: List<StatementDataModel>, allCheckModels: List<CheckDataModel>): Pair<List<StatementDataModel>, List<CheckDataModel>> {
        val allAccountNumbers = allStatementModels.mapNotNull { it.accountNumber }.toSet() + allCheckModels.mapNotNull { it.accountNumber }.toSet()
        val mapping = getAccountMapping(allAccountNumbers)
        logger.info { "Account mapping: $mapping" }

        return Pair(
            allStatementModels.map { it.copy(accountNumber = mapping[it.accountNumber] ?: it.accountNumber) },
            allCheckModels.map { it.copy(accountNumber = mapping[it.accountNumber] ?: it.accountNumber) }
        )
    }

    fun getAccountMapping(originalAccounts: Set<String>): Map<String, String> {
        val originalAccountsWithValidNumbers = originalAccounts.filter { accountNumber ->
            accountNumber.filter { it.isDigit() }.length >= 4
        }
        val originalToNumberOnlyMapping = originalAccountsWithValidNumbers.groupBy { it.filter { it.isDigit() } }

        val sortedAccountNumbers = originalAccountsWithValidNumbers.map { it.filter { it.isDigit() } }.sortedByDescending { it.length }
        val resultMap = mutableMapOf<String, String>()

        for (longestAccount in sortedAccountNumbers) {
            if (resultMap.keys.none { it.endsWith(longestAccount) }) {
                for (account in sortedAccountNumbers.filter { longestAccount.endsWith(it) }) {
                    originalToNumberOnlyMapping[account]?.forEach {
                        resultMap[it] = longestAccount
                    }
                }
            }
        }

        return resultMap
    }
}