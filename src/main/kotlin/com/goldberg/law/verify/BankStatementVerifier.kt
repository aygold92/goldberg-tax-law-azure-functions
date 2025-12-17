package com.goldberg.law.verify

import com.goldberg.law.document.model.input.StatementDataModel.Keys
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.Statement
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.toCurrency
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

class BankStatementVerifier @Inject constructor(private val transactionVerifier: TransactionVerifier) {
    private val logger = KotlinLogging.logger {}

    fun getSuspiciousReasons(statement: Statement) = getSuspiciousReasons(statement.statementDetails, statement.transactions, statement.classification)

    fun getSuspiciousReasons(statement: StatementDetails, transactions: List<TransactionDetails>, classification: Classification): List<String> = mutableListOf<String>().apply {
        addAll(statement.getMissingFields().map { SuspiciousReasons.MISSING_FIELDS.format(it) })
        val netTransactions = getNetTransactions(transactions)
        if (!statement.numbersAddUp(netTransactions, classification)) {
            val expected = if (statement.beginningBalance == null || statement.endingBalance == null) null
            else if (classification.isCreditCard()) statement.beginningBalance.asCurrency() - statement.endingBalance.asCurrency()!!
            else statement.endingBalance - statement.beginningBalance
            add(SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(statement.beginningBalance?.toCurrency(), netTransactions.toCurrency(), statement.endingBalance?.toCurrency(), expected?.toCurrency()))
        }
        if (transactions.isEmpty()) add(SuspiciousReasons.NO_TRANSACTIONS_FOUND)

        val transactionSuspiciousReasons = transactions.map { transactionVerifier.getSuspiciousReasons(statement.statementDate(), it) }.flatten()
        if (transactionSuspiciousReasons.isNotEmpty()) add(SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS)
        addAll(transactionSuspiciousReasons)
    }

    private fun Classification.isCreditCard(): Boolean = documentType == DocumentType.CREDIT_CARD

    private fun StatementDetails.getMissingFields(): List<String> = listOf(
        Pair(Keys.STATEMENT_DATE, date),
        Pair(Keys.ACCOUNT_NUMBER, accountNumber),
        Pair(Keys.BEGINNING_BALANCE, beginningBalance),
        Pair(Keys.ENDING_BALANCE, endingBalance)
    ).filter { it.second == null }.map { it.first }

    // for some reason when you do math on BigDecimal it might add trailing 0s, so we need to get rid of them
    private fun StatementDetails.numbersAddUpBank(netTransactions: BigDecimal, beginningBalance: BigDecimal, endingBalance: BigDecimal): Boolean =
        (beginningBalance + netTransactions).stripTrailingZeros() == endingBalance.stripTrailingZeros()

    // net transactions are reversed on a credit card since spending (positive balance on the physical statement) is stored as a negative cash flow
    // for that reason we subtract the net transactions to get to endingBalance
    //
    // we then add in interest, fees, or both to see if they add up, because some statements include it in transactions and others don't
    // TODO: maybe some logic to check if it's already included?
    private fun StatementDetails.numbersAddUpCreditCard(netTransactionsOrig: BigDecimal, beginningBalance: BigDecimal, endingBalance: BigDecimal): Boolean = netTransactionsOrig.negate().let { netTransactions ->
        val interest = interestCharged ?: ZERO
        val fees = feesCharged ?: ZERO
        val endBalance = endingBalance.stripTrailingZeros()
        // we've already ensured not null on beginningBalance/endingBalance when we call this function
        (beginningBalance + netTransactions).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + interest).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + fees).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + interest + fees).stripTrailingZeros() == endBalance
    }


    private fun StatementDetails.numbersAddUp(netTransactions: BigDecimal, classification: Classification): Boolean {
        // the statement will be flagged as suspicious already for null values, we don't care about this here
        if (beginningBalance == null || endingBalance == null) return true

        return when (classification.documentType) {
            DocumentType.BANK -> numbersAddUpBank(netTransactions, beginningBalance, endingBalance)
            DocumentType.CREDIT_CARD -> numbersAddUpCreditCard(netTransactions, beginningBalance, endingBalance)
            else -> numbersAddUpBank(netTransactions, beginningBalance, endingBalance) || numbersAddUpCreditCard(netTransactions, beginningBalance, endingBalance)
        }
    }

    private fun getNetTransactions(transactions: List<TransactionDetails>): BigDecimal =
        getTotalIncomeCredits(transactions) - getTotalSpending(transactions)

    private fun getTotalSpending(transactions: List<TransactionDetails>) = transactions
        .map { it.amount ?: 0.asCurrency() }
        .filter { it < 0.asCurrency() }
        .takeIf { it.isNotEmpty() }
        ?.reduce {acc, amt -> (acc + amt) }?.abs() ?: 0.asCurrency()

    private fun getTotalIncomeCredits(transactions: List<TransactionDetails>): BigDecimal = transactions
        .map { it.amount ?: 0.asCurrency() }
        .filter { it > 0.asCurrency() }
        .takeIf { it.isNotEmpty() }
        ?.reduce {acc, amt -> (acc + amt) }?.abs() ?: 0.asCurrency()

//    fun hasNoRecords(): Boolean = transactions.isEmpty()

    object SuspiciousReasons {
        const val MISSING_FIELDS = "Missing fields: %s"
        const val BALANCE_DOES_NOT_ADD_UP = "Beginning balance (%s) + net transactions (%s) != ending balance (%s). Expected (%s)"
        const val NO_TRANSACTIONS_FOUND = "No transactions recorded"
        const val CONTAINS_SUSPICIOUS_RECORDS = "Contains suspicious records"
    }
}