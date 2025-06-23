package com.goldberg.law.document

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import io.github.oshai.kotlinlogging.KotlinLogging

class CheckToStatementMatcher {
    private val logger = KotlinLogging.logger {}

    fun matchChecksWithStatements(allStatements: List<BankStatement>, allChecks: List<CheckDataModel>): List<BankStatement> {
        val allChecksMap = allChecks.associateBy { it.checkDataKey }

        val finalStatements = allStatements.sortedBy { it.statementDate }.map { originalStatement ->
            val checks = mutableMapOf<Int, ClassifiedPdfMetadata>()
            val transactions = originalStatement.transactions.map { record ->
                if (record.checkNumber == null) record
                else {
                    val checkModel = allChecksMap[CheckDataKey(originalStatement.accountNumber, record.checkNumber)]
                        ?: allChecksMap.values.find { it.matches(record) }
                    if (checkModel?.checkNumber != null) {
                        checks[checkModel.checkNumber] = checkModel.pageMetadata
                        record.withCheckInfo(checkModel)
                    } else {
                        record
                    }
                }
            }.toMutableList()
            originalStatement.copy(
                transactions = transactions,
                checks = checks
            )
        }
        return finalStatements.also {
            logger.debug { "Missing Check Images: ${it.getMissingChecks()}" }
            logger.debug { "Checks without matching transactions: ${it.getChecksNotUsed(allChecks.map { it.checkDataKey }.toSet())}" }
        }
    }
}

fun List<BankStatement>.getMissingChecks(): Set<CheckDataKey> = this.flatMap { it.getMissingChecks() }.toSet()
fun List<BankStatement>.getChecksUsed(): List<CheckDataModel> = this.flatMap { it.getCheckModelsUsed() }
fun List<BankStatement>.getChecksNotUsed(allChecks: Set<CheckDataKey>): Set<CheckDataKey> =
    allChecks - this.flatMap { it.getCheckKeysUsed() }.toSet()
