package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*

class FIFOSplitCashTransactionProcessor: MaritalTransactionProcessor() {
    private val logger = KotlinLogging.logger {}

    override fun processMaritalTransactions(
        transactions: List<InvestmentTransaction>,
        startingHoldings: HoldingsReport,
        marriageDate: Date
    ): ProcessMaritalTransactionsOutput {
        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
        val transactionLog: MutableList<TransactionLog> = mutableListOf()

        val cashSym = transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
            .map { it.symbol }.distinct()
            .let { cashAdjacentSymbols -> cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException("Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}") }
            .first()

        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.preMaritalHoldings.minus(cashSym).toMutableMap()
        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.sharedHoldings.minus(cashSym).toMutableMap()

        val cashQueue = CashQueue()
        val (startingAmountPreMarital, startingAmountMarital) = Pair(startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO, startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)
        val startingRatio = calculateRatio(startingAmountPreMarital, startingAmountMarital)
        cashQueue.add(startingAmountPreMarital + startingAmountMarital, startingRatio)

        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()

        val sortedTransactions = transactions.sortedBy { it.date }

        val pairedDistributionIndices: Set<Int> = sortedTransactions.filter { it.type == TransactionType.RE_INVESTMENT }.mapNotNull { trans ->
            sortedTransactions.indexOfFirst {
                it.type == TransactionType.DISTRIBUTION && it.symbol == trans.symbol && it.date == trans.date && it.amount == trans.amount
            }.takeIf { it >= 0 }
        }.toSet()

        var currentStatementDate = sortedTransactions[0].getStatementDate()

        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
        sortedTransactions.forEachIndexed { idx, transaction ->
            try {
                if (transaction.getStatementDate() != currentStatementDate) {
                    val (preMaritalCashHoldings, sharedCashHoldings) = cashQueue.totalByClassification()
                    holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(
                        preMaritalHoldings.plus(cashSym to preMaritalCashHoldings).toMap(),
                        sharedHoldings.plus(cashSym to sharedCashHoldings).toMap()
                    )
                    logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }
                    currentStatementDate = transaction.getStatementDate()
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] processing a ${transaction.type} for ${transaction.symbol}: qty=${transaction.quantity}, amt=${transaction.amount}" }

                val percent: BigDecimal? = when(transaction.type) {
                    TransactionType.BUY -> {
                        // we buy with the cash in the account
                        val (preMaritalCashPolled, sharedCashPolled) = cashQueue.poll(transaction.amount)
                        val preMaritalCashPercent = calculateRatio(preMaritalCashPolled, sharedCashPolled)

                        val preMaritalQuantityOrAmount = transaction.quantity!!.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                        sharedHoldings.add(transaction.symbol, transaction.quantity!! - preMaritalQuantityOrAmount)

                        preMaritalCashPercent
                    }
                    TransactionType.SELL -> {
                        // sells are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO), sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO))
                        val preMaritalInstrumentAmount = (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)

                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent

                        cashQueue.add(transaction.amount, preMaritalInstrumentPercent)

                        preMaritalInstrumentPercent
                    }
                    TransactionType.RE_INVESTMENT -> {
                        // reInvestments are made in proportion of current ownership; the paired DISTRIBUTION handles cash
                        val (preMaritalQuantity, sharedQuantity) = if (transaction.symbol == cashSym) cashQueue.totalByClassification()
                        else Pair(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO)
                        )

                        val preMaritalInstrumentPercent = calculateRatio(preMaritalQuantity, sharedQuantity,lastKnownRatios[transaction.symbol])

                        val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)

                        if (transaction.symbol == cashSym) {
                            cashQueue.add(totalQuantityOrAmount, preMaritalInstrumentPercent)
                        } else {
                            val preMaritalQuantityOrAmount = totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
                            preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                            sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)
                        }

                        preMaritalInstrumentPercent
                    }
                    TransactionType.DISTRIBUTION -> {
                        // distributions are made in proportion of current ownership.  It could be interest or dividend from money market
                        val (preMaritalQuantity, sharedQuantity) = if (transaction.symbol == cashSym) cashQueue.totalByClassification()
                        else Pair(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO)
                        )

                        val preMaritalInstrumentPercent = calculateRatio(preMaritalQuantity, sharedQuantity,lastKnownRatios[transaction.symbol])
                        // paired distributions (those with a corresponding RE_INVESTMENT) bypass the cash queue
                        if (idx !in pairedDistributionIndices) {
                            cashQueue.add(transaction.amount, preMaritalInstrumentPercent)
                        }
                        preMaritalInstrumentPercent
                    }
                    TransactionType.DEPOSIT -> {
                        // deposits before marriage are 100% preMarital, and after is 100% shared
                        if (transaction.classificationOverride == TransactionClassification.Marital ||
                            (transaction.date.after(marriageDate) && transaction.classificationOverride != TransactionClassification.PreMarital)) {
                            cashQueue.add(transaction.amount, BigDecimal.ZERO)
                            BigDecimal.ZERO
                        } else {
                            cashQueue.add(transaction.amount, BigDecimal.ONE)
                            BigDecimal.ONE
                        }
                    }
                    TransactionType.WITHDRAWAL -> {
                        // withdrawals are made based on pulling from the FIFO cash queue
                        val (preMaritalCashToUse, sharedCashToUse) = cashQueue.poll(transaction.amount)
                        val preMaritalCashPercent = calculateRatio(preMaritalCashToUse, sharedCashToUse)
                        preMaritalCashPercent
                    }
                    else -> {
                        null
                    }
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] preMaritalPercent=$percent" }
                transactionLog.add(transaction.log(percent))
                currentStatementDate = transaction.getStatementDate()
            } catch(ex: Exception) {
                logger.error(ex) { "Error processing transaction $idx: $transaction" }
                throw ex
            }
        }
        val (preMaritalCashHoldings, sharedCashHoldings) = cashQueue.totalByClassification()
        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(
            preMaritalHoldings.plus(cashSym to preMaritalCashHoldings).toMap(),
            sharedHoldings.plus(cashSym to sharedCashHoldings).toMap()
        )
        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }

        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
    }
}