package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InvestmentTransaction
import com.goldberg.law.script.maritalinvestments.model.TransactionClassification
import com.goldberg.law.script.maritalinvestments.model.TransactionLog
import com.goldberg.law.script.maritalinvestments.model.TransactionType
import com.goldberg.law.util.bd
import com.goldberg.law.util.clean
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.MathContext
import java.util.Calendar
import java.util.Date

abstract class MaritalTransactionProcessor(
//    val transactions: List<InvestmentTransaction>,
//    val startingHoldings: HoldingsReport,
//    val marriageDate: Date
) {
    private val logger = KotlinLogging.logger {}

    abstract fun processMaritalTransactions(
        transactions: List<InvestmentTransaction>,
        startingHoldings: HoldingsReport,
        marriageDate: Date
    ): ProcessMaritalTransactionsOutput

//    abstract fun initCashHolding(cashSym: InstrumentKey)
//    abstract fun getCurrentCashTotals(currentStatementDate: Date): Pair<BigDecimal, BigDecimal>
//    abstract fun processBuyTransaction(transaction: InvestmentTransaction)
//
//
//    fun processMaritalTransactions(): ProcessMaritalTransactionsOutput {
//        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
//        val transactionLog: MutableList<TransactionLog> = mutableListOf()
//
//        val cashSym =
//            transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
//                .map { it.symbol }.distinct()
//                .let { cashAdjacentSymbols ->
//                    cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException(
//                        "Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}"
//                    )
//                }
//                .first()
//
//        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> =
//            startingHoldings.preMaritalHoldings.minus(cashSym).toMutableMap()
//        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> =
//            startingHoldings.sharedHoldings.minus(cashSym).toMutableMap()
//
//        initCashHolding(cashSym)
//
//        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
//        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()
//
//        val sortedTransactions = transactions.sortedBy { it.date }
//
//        var currentStatementDate = sortedTransactions[0].getStatementDate()
//
//        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
//        sortedTransactions.forEachIndexed { idx, transaction ->
//            try {
//                if (transaction.getStatementDate() != currentStatementDate) {
//                    val (preMaritalCashHoldings, sharedCashHoldings) = getCurrentCashTotals()
//                    holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(
//                        preMaritalHoldings.plus(cashSym to preMaritalCashHoldings).toMap(),
//                        sharedHoldings.plus(cashSym to sharedCashHoldings).toMap()
//                    )
//                    logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}" }
//                    currentStatementDate = transaction.getStatementDate()
//                }
//                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] processing a ${transaction.type} for ${transaction.symbol}: qty=${transaction.quantity}, amt=${transaction.amount}" }
//
//                val percent: BigDecimal? = when (transaction.type) {
//                    TransactionType.BUY -> {
//                        // we buy with the cash in the account
//                        val (preMaritalCashPolled, sharedCashPolled) = cashQueue.poll(transaction.amount)
//                        val preMaritalCashPercent = calculateRatio(preMaritalCashPolled, sharedCashPolled)
//
//                        val preMaritalQuantityOrAmount = transaction.quantity!!.multiplyScale(preMaritalCashPercent)
//                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
//                        sharedHoldings.add(transaction.symbol, transaction.quantity!! - preMaritalQuantityOrAmount)
//
//                        preMaritalCashPercent
//                    }
//
//                    TransactionType.SELL -> {
//                        // sells are made in proportion of current ownership
//                        val preMaritalInstrumentPercent = calculateRatio(
//                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
//                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO)
//                        )
//                        val preMaritalInstrumentAmount =
//                            (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
//                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
//                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)
//
//                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent
//
//                        cashQueue.add(transaction.amount, preMaritalInstrumentPercent)
//
//                        preMaritalInstrumentPercent
//                    }
//
//                    TransactionType.RE_INVESTMENT -> {
//                        // reInvestments are made in proportion of current ownership; the paired DISTRIBUTION handles cash
//                        val preMaritalInstrumentPercent = calculateRatio(
//                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
//                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
//                            lastKnownRatios[transaction.symbol]
//                        )
//
//                        val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)
//                        val preMaritalQuantityOrAmount =
//                            totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
//                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
//                        sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)
//
//                        preMaritalInstrumentPercent
//                    }
//
//                    TransactionType.DISTRIBUTION -> {
//                        // distributions are made in proportion of current ownership
//                        val preMaritalInstrumentPercent = calculateRatio(
//                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
//                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
//                            lastKnownRatios[transaction.symbol]
//                        )
//                        // paired distributions (those with a corresponding RE_INVESTMENT) bypass the cash queue
//                        if (idx !in pairedDistributionIndices) {
//                            cashQueue.add(transaction.amount, preMaritalInstrumentPercent)
//                        }
//                        preMaritalInstrumentPercent
//                    }
//
//                    TransactionType.DEPOSIT -> {
//                        // deposits before marriage are 100% preMarital, and after is 100% shared
//                        if (transaction.classificationOverride == TransactionClassification.Marital ||
//                            (transaction.date.after(marriageDate) && transaction.classificationOverride != TransactionClassification.PreMarital)
//                        ) {
//                            cashQueue.add(transaction.amount, BigDecimal.ZERO)
//                            BigDecimal.ZERO
//                        } else {
//                            cashQueue.add(transaction.amount, BigDecimal.ONE)
//                            BigDecimal.ONE
//                        }
//                    }
//
//                    TransactionType.WITHDRAWAL -> {
//                        // withdrawals are made based on pulling from the FIFO cash queue
//                        val (preMaritalCashToUse, sharedCashToUse) = cashQueue.poll(transaction.amount)
//                        val preMaritalCashPercent = calculateRatio(preMaritalCashToUse, sharedCashToUse)
//                        preMaritalCashPercent
//                    }
//
//                    else -> {
//                        null
//                    }
//                }
//                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] preMaritalPercent=$percent" }
//                transactionLog.add(transaction.log(percent))
//                currentStatementDate = transaction.getStatementDate()
//            } catch (ex: Exception) {
//                logger.error(ex) { "Error processing transaction $idx: $transaction" }
//                throw ex
//            }
//        }
//        val (preMaritalCashHoldings, sharedCashHoldings) = getCurrentCashTotals()
//        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(
//            preMaritalHoldings.plus(cashSym to preMaritalCashHoldings).toMap(),
//            sharedHoldings.plus(cashSym to sharedCashHoldings).toMap()
//        )
//        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}" }
//
//        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
//    }

    protected fun calculateRatio(val1: BigDecimal, val2: BigDecimal, fallbackRatio: BigDecimal? = null): BigDecimal {
        if (val1 < 0.bd() || val1 + val2 <= 0.bd()) {
            logger.error { "Invalid ratio calculation: $val1, $val2" }
        }
        val ratio = try {
            val1.divide((val1 + val2), MathContext.DECIMAL128).clean()
        } catch (ex: Exception) {
            logger.error(ex) { "Error dividing $val1 by $val2" }
            fallbackRatio ?: throw ex
        }
        return ratio
    }

    companion object {
        fun MutableMap<InstrumentKey, BigDecimal>.subtract(symbol: InstrumentKey, amount: BigDecimal) = add(symbol, amount.negate())
        fun MutableMap<InstrumentKey, BigDecimal>.add(symbol: InstrumentKey, amount: BigDecimal) {
            try {

                this[symbol] = (this.getOrDefault(symbol, 0.bd()) + amount).clean()
            } catch (ex: Exception) {
                println(ex)
            }
        }

        fun BigDecimal.multiplyScale(other: BigDecimal) = (this * other).clean()

        // statement dates are considered to be the first of the next month, including any transactions on the first of the month
        // for ex, a transaction on 10/2/2020 and 11/1/2020 will be both have the statement date 11/1/2020
        fun InvestmentTransaction.getStatementDate(): Date = Calendar.getInstance().apply { time = this@getStatementDate.date }.let { cal ->
            if (cal.get(Calendar.DAY_OF_MONTH) == 1) {
                cal.time
            } else {
                cal.add(Calendar.MONTH, 1)
                cal.set(Calendar.DAY_OF_MONTH, 1)
                cal.time
            }
        }
    }
}