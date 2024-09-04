package com.goldberg.law.document

import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.CheckData
import com.goldberg.law.document.model.output.CheckDataKey
import io.github.oshai.kotlinlogging.KotlinLogging

class CheckToStatementMatcher {
    private val logger = KotlinLogging.logger {}

    fun matchChecksWithStatements(allStatements: List<BankStatement>, allChecksMap: Map<CheckDataKey, CheckData>): Triple<List<BankStatement>, Set<CheckDataKey>, Set<CheckDataKey>> {
        val statementAccountToCheckAccount = getStatementToCheckAccountMapping(allChecksMap, allStatements)
        val checksUsed = mutableSetOf<CheckDataKey>()
        val checksNotFound = mutableSetOf<CheckDataKey>()
        val finalStatements = allStatements.sortedBy { it.statementDate }.map { originalStatement ->
            val transactions = originalStatement.transactions.map { record ->
                if (record.checkNumber != null) {
                    val checkDataKey = CheckDataKey(statementAccountToCheckAccount[originalStatement.accountNumber], record.checkNumber)

                    val check = allChecksMap[checkDataKey]
                    if (check != null) {
                        checksUsed.add(checkDataKey)
                        record.withCheckInfo(check)
                    } else {
                        val matchingCheck = allChecksMap.values.find { it.matches(record) }
                        if (matchingCheck != null) {
                            checksUsed.add(matchingCheck.checkDataKey)
                            record.withCheckInfo(matchingCheck)
                        } else {
                            checksNotFound.add(checkDataKey.copy(accountNumber = originalStatement.accountNumber))
                            record
                        }
                    }
                } else {
                    record
                }
            }.toMutableList()
            originalStatement.copy(transactions = transactions)
        }
        val checksNotUsed = allChecksMap.keys - checksUsed.toSet()
        return Triple(finalStatements, checksNotFound, checksNotUsed).also {
            logger.debug { "Missing Check Images: $checksNotFound" }
            logger.debug { "Checks without matching transactions: $checksNotUsed" }
        }
    }

    fun getStatementToCheckAccountMapping(allChecksMap: Map<CheckDataKey, CheckData>, allStatements: List<BankStatement>): Map<String, String> {
        val statementAccountToCheckAccount = mutableMapOf<String, String>()
        val allCheckAccountNumbers = allChecksMap.keys.mapNotNull { it.accountNumber }.toSet()
        val allStatementAccountNumbers = allStatements.mapNotNull { it.accountNumber }.toSet()
        allStatementAccountNumbers.forEach { accountNumberOnStatement ->
            val numberOnStatementToUse = accountNumberOnStatement.filter { it.isDigit() }
            if (numberOnStatementToUse.length >= 4) {
                val matchingAccounts = allCheckAccountNumbers.filter { accountNumberOnCheck ->
                    accountNumberOnCheck.endsWith(numberOnStatementToUse)
                }
                when(matchingAccounts.size) {
                    1 -> {
                        val other = statementAccountToCheckAccount.putIfAbsent(accountNumberOnStatement, matchingAccounts[0])
                        if (other != null) {
                            logger.error { "Found duplicate account endings: $accountNumberOnStatement, $other" }
                        }
                    }
                    0 -> logger.error { "No Matching accounts" }
                    else -> logger.error { "Multiple accounts match $accountNumberOnStatement: $matchingAccounts" }
                }
            } else {
                logger.error { "Invalid Account Number found for statement: $accountNumberOnStatement" }
            }
        }
        return statementAccountToCheckAccount
    }
}