package com.goldberg.law.function.activity

import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.function.activity.model.MatchChecksToStatementsActivityInput
import com.goldberg.law.function.activity.model.MatchChecksToStatementsActivityOutput
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import com.google.inject.Inject

class MatchChecksToStatementsActivity@Inject constructor(
    private val transactionService: TransactionService,
) {
    private val logger = KotlinLogging.logger {}
    @FunctionName(FUNCTION_NAME)
    fun matchChecksToStatements(
        @DurableActivityTrigger(name = "input") input: MatchChecksToStatementsActivityInput,
        context: ExecutionContext
    ): MatchChecksToStatementsActivityOutput {
        val checkIds = input.documents.flatMap { it.value.checkIds }.toSet()
        val statementIds = input.documents.flatMap { it.value.statementIds }.toSet()
        logger.info { "[${input.requestId}][${context.invocationId}] processing checks for $checkIds checks and $statementIds statements" }

//        val transactionsWithMatchingChecks = input.transactions.mapNotNull { (statementDetails, transactionDetails) ->
//            input.checks.find { transactionDetails.checkNumber == it.checkNumber && statementDetails.accountNumber == it.accountNumber }
//                ?.let { matchingCheck -> transactionDetails.transactionId to matchingCheck }
//        }.toMap()
//
//        val checksWithMatchingTransactions = input.checks.mapNotNull { (checkId, checkNumber, accountNumber) ->
//             input.transactions.find { checkNumber == it.transactionDetails.checkNumber && accountNumber == it.statementDetails.accountNumber }
//                 ?.let { matchingTransaction -> checkId to matchingTransaction }
//        }.toMap()

        val matchingTransactionsChecks = transactionService.findMatchingChecks(input.clientId)
            // TODO: should we only operate on the ones in this batch?
            .filter { transaction ->
                statementIds.contains(transaction.statementId) || checkIds.contains(transaction.checkId)
            }

        transactionService.linkTransactionsWithChecks(
            matchingTransactionsChecks.map { tr -> TransactionCheckMatch(tr.transactionId, tr.checkId!!)}
        )

        return MatchChecksToStatementsActivityOutput(matchingTransactionsChecks).also {
            logger.info { "[${input.requestId}] Matched statements and checks: ${it.matchedTransactions}" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "MatchChecksToStatements"
    }
}