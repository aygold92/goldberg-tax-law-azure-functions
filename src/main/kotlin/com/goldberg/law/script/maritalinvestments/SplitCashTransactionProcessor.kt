package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*

class SplitCashTransactionProcessor: MaritalTransactionProcessor() {
    private val logger = KotlinLogging.logger {}

    override fun processMaritalTransactions(transactions: List<InvestmentTransaction>,
                                   startingHoldings: HoldingsReport,
                                   marriageDate: Date): ProcessMaritalTransactionsOutput {
        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
        val transactionLog: MutableList<TransactionLog> = mutableListOf()

        val cashSym = transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
            .map { it.symbol }.distinct()
            .let { cashAdjacentSymbols -> cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException("Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}") }
            .first()

        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> = if (!startingHoldings.preMaritalHoldings.containsKey(cashSym))
            startingHoldings.preMaritalHoldings.plus(mutableMapOf(cashSym to BigDecimal.ZERO)).toMutableMap()
        else startingHoldings.preMaritalHoldings.toMutableMap()

        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> = if (!startingHoldings.sharedHoldings.containsKey(cashSym))
            startingHoldings.sharedHoldings.plus(mutableMapOf(cashSym to BigDecimal.ZERO)).toMutableMap()
        else startingHoldings.sharedHoldings.toMutableMap()

        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()

        val sortedTransactions = transactions.sortedBy { it.date }

        var currentStatementDate = sortedTransactions[0].getStatementDate()

        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
        sortedTransactions.forEachIndexed { idx, transaction ->
            try {
                if (transaction.getStatementDate() != currentStatementDate) {
                    holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
                    logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }
                    currentStatementDate = transaction.getStatementDate()
                }
                logger.info { "[$idx] [${transaction.date.toTransactionDate()}] processing a ${transaction.type} for ${transaction.symbol}: qty=${transaction.quantity}, amt=${transaction.amount}" }

                val percent: BigDecimal? = when(transaction.type) {
                    TransactionType.BUY -> {
                        // we buy with the cash in the account
                        val preMaritalCashPercent = calculateRatio(preMaritalHoldings[cashSym]!!, sharedHoldings[cashSym]!!)

                        val preMaritalQuantityOrAmount = transaction.quantity!!.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                        sharedHoldings.add(transaction.symbol, transaction.quantity!! - preMaritalQuantityOrAmount)

                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)

                        preMaritalCashPercent
                    }
                    TransactionType.SELL -> {
                        // sells are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO), sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO))
                        val preMaritalInstrumentAmount = (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)

                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent

                        val preMaritalAmount = (transaction.amount).multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(cashSym, preMaritalAmount)
                        sharedHoldings.add(cashSym, transaction.amount - preMaritalAmount)

                        preMaritalInstrumentPercent
                    }
                    TransactionType.RE_INVESTMENT -> {
                        // reInvestments are like buys but are made the proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            lastKnownRatios[transaction.symbol]
                        )

                        val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)
                        val preMaritalQuantityOrAmount = totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                        sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)

                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)
                        preMaritalInstrumentPercent
                    }
                    TransactionType.DISTRIBUTION -> {
                        // distributions are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(
                            preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                            lastKnownRatios[transaction.symbol]
                        )
                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalInstrumentPercent)
                        preMaritalHoldings.add(cashSym, preMaritalAmount)
                        sharedHoldings.add(cashSym, transaction.amount - preMaritalAmount)
                        preMaritalInstrumentPercent
                    }
                    TransactionType.DEPOSIT -> {
                        // deposits before marriage are 100% preMarital, and after is 100% shared
                        if (transaction.classificationOverride == TransactionClassification.Marital ||
                            (transaction.date.after(marriageDate) && transaction.classificationOverride != TransactionClassification.PreMarital)) {
                            sharedHoldings.add(cashSym, transaction.amount)
                            BigDecimal.ZERO
                        } else {
                            preMaritalHoldings.add(cashSym, transaction.amount)
                            BigDecimal.ONE
                        }
                    }
                    TransactionType.WITHDRAWAL -> {
                        // withdrawals are made based on proportion of cash in the account
                        val preMaritalCashPercent = calculateRatio(preMaritalHoldings[cashSym]!!, sharedHoldings[cashSym]!!)
                        val preMaritalAmount = transaction.amount.multiplyScale(preMaritalCashPercent)
                        preMaritalHoldings.subtract(cashSym, preMaritalAmount)
                        sharedHoldings.subtract(cashSym, transaction.amount - preMaritalAmount)
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
        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }

        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
    }
}