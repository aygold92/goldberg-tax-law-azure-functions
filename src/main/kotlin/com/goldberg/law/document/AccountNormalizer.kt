package com.goldberg.law.document

import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.CheckData
import com.goldberg.law.document.model.output.CheckDataKey
import io.github.oshai.kotlinlogging.KotlinLogging

class AccountNormalizer {
    private val logger = KotlinLogging.logger {}

    fun normalizeAccounts(allStatements: List<BankStatement>, allChecksMap: Map<CheckDataKey, CheckData>): Pair<List<BankStatement>, Map<CheckDataKey, CheckData>> {
        val allAccountNumbers = allChecksMap.keys.mapNotNull { it.accountNumber }.toSet() + allStatements.mapNotNull { it.accountNumber }.toSet()

        val mapping = getAccountMapping(allAccountNumbers)
        logger.info { "Account mapping: $mapping" }

        val newChecksMapping = allChecksMap.map {
            val correctAccount = mapping[it.key.accountNumber] ?: it.key.accountNumber
            CheckDataKey(correctAccount, it.key.checkNumber) to it.value.copy(accountNumber = correctAccount)
        }.toMap()
        val newStatementList = allStatements.map { it.copy(accountNumber = mapping[it.accountNumber] ?: it.accountNumber) }
        return Pair(newStatementList, newChecksMapping)
    }

    fun getAccountMapping(originalAccounts: Set<String>): Map<String, String> {
        val originalAccountsWithValidNumbers = originalAccounts.filter { it.filter { it.isDigit() }.length >= 4 }
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