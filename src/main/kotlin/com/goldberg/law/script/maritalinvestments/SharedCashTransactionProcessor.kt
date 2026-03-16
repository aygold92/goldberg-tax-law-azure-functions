package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InvestmentTransaction
import com.goldberg.law.script.maritalinvestments.model.TransactionLog
import com.goldberg.law.script.maritalinvestments.model.TransactionType
import com.goldberg.law.util.clean
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*

class SharedCashTransactionProcessor: MaritalTransactionProcessor() {
//class SharedCashTransactionProcessor(transactions: List<InvestmentTransaction>,
//                                     startingHoldings: HoldingsReport,
//                                     marriageDate: Date): MaritalTransactionProcessor(transactions, startingHoldings, marriageDate) {
    private val logger = KotlinLogging.logger {}
    private var cashHoldings = BigDecimal.ZERO

//    override fun initCashHolding(cashSym: InstrumentKey) {
//        cashHoldings = ((startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO) + (startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)).clean()
//    }
//
//    override fun getCurrentCashTotals(currentStatementDate: Date): Pair<BigDecimal, BigDecimal> {
//        return if (currentStatementDate.after(marriageDate)) Pair(BigDecimal.ZERO, cashHoldings)
//        else Pair(cashHoldings, BigDecimal.ZERO)
//    }
//
//    override fun processBuyTransaction(transaction: InvestmentTransaction) {
//        TODO("Not yet implemented")
//    }
    // in this method, we assume all cash is shared
    override fun processMaritalTransactions(transactions: List<InvestmentTransaction>,
                                             startingHoldings: HoldingsReport,
                                             marriageDate: Date): ProcessMaritalTransactionsOutput {
        val holdingsReport: MutableMap<String, HoldingsReport> = mutableMapOf()
        val transactionLog: MutableList<TransactionLog> = mutableListOf()

        val cashSym = transactions.filter { it.type == TransactionType.SWEEP_IN || it.type == TransactionType.SWEEP_OUT }
            .map { it.symbol }.distinct()
            .let { cashAdjacentSymbols -> cashAdjacentSymbols.takeIf { it.size == 1 } ?: throw IllegalArgumentException("Exactly 1 cash adjacent symbol is required. Found: ${cashAdjacentSymbols.joinToString(", ")}") }
            .first()

        val preMaritalHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.preMaritalHoldings.minus(cashSym).toMutableMap()
        val sharedHoldings: MutableMap<InstrumentKey, BigDecimal> = startingHoldings.sharedHoldings.minus(cashSym).toMutableMap()
        // if we sell an asset to 0, remember the last known ratio in case there is a DISTRIBUTION that comes after
        val lastKnownRatios: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf()

        // keep track of the shared cash
        var cashHoldings = ((startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO) + (startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)).clean()

        val sortedTransactions = transactions.sortedBy { it.date }

        var currentStatementDate = sortedTransactions[0].getStatementDate()

        logger.info { "initial holdings: $preMaritalHoldings, $sharedHoldings" }
        sortedTransactions.forEachIndexed { idx, transaction ->
            try {
                val isPostMarriage = transaction.date.after(marriageDate)
                if (transaction.getStatementDate() != currentStatementDate) {
                    val preMaritalCashHoldings = if (currentStatementDate.after(marriageDate)) BigDecimal.ZERO else cashHoldings
                    val sharedCashHoldings = if (currentStatementDate.after(marriageDate)) cashHoldings else BigDecimal.ZERO
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
                        if (isPostMarriage) sharedHoldings.add(transaction.symbol, transaction.quantity!!)
                        else preMaritalHoldings.add(transaction.symbol, transaction.quantity!!)

                        cashHoldings = (cashHoldings - transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.SELL -> {
                        // sells are made in proportion of current ownership
                        val preMaritalInstrumentPercent = calculateRatio(preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO), sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO))
                        val preMaritalInstrumentAmount = (transaction.quantity!!.multiplyScale(preMaritalInstrumentPercent))
                        preMaritalHoldings.subtract(transaction.symbol, preMaritalInstrumentAmount)
                        sharedHoldings.subtract(transaction.symbol, transaction.quantity!! - preMaritalInstrumentAmount)

                        lastKnownRatios[transaction.symbol] = preMaritalInstrumentPercent

                        cashHoldings = (cashHoldings + transaction.amount).clean()

                        preMaritalInstrumentPercent
                    }
                    TransactionType.RE_INVESTMENT -> {
                        // reInvestments are like buys but are made the proportion of current ownership
                        if (transaction.symbol == cashSym) {
                            // no need to add or subtract from cash holdings because it cancels out, and the addition is already handled by the dividend
                            if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                        } else {
                            val preMaritalInstrumentPercent = calculateRatio(
                                preMaritalHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                                sharedHoldings.getOrDefault(transaction.symbol, BigDecimal.ZERO),
                                lastKnownRatios[transaction.symbol]
                            )

                            // reinvestments could be for the money market fund (which uses amount only) or for a symbol (which uses quantity)
                            val totalQuantityOrAmount = (transaction.quantity ?: transaction.amount)
                            val preMaritalQuantityOrAmount = totalQuantityOrAmount.multiplyScale(preMaritalInstrumentPercent)
                            preMaritalHoldings.add(transaction.symbol, preMaritalQuantityOrAmount)
                            sharedHoldings.add(transaction.symbol, totalQuantityOrAmount - preMaritalQuantityOrAmount)

                            cashHoldings = (cashHoldings - transaction.amount).clean()
                            preMaritalInstrumentPercent
                        }
                    }
                    TransactionType.DISTRIBUTION -> {
                        // distributions are made in proportion of current ownership
                        cashHoldings = (cashHoldings + transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.DEPOSIT -> {
                        // deposits before marriage are 100% preMarital, and after is 100% shared
                        cashHoldings = (cashHoldings + transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
                    }
                    TransactionType.WITHDRAWAL -> {
                        // withdrawals just subtract from cash
                        cashHoldings = (cashHoldings - transaction.amount).clean()
                        if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
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
        preMaritalHoldings[cashSym] = BigDecimal.ZERO
        sharedHoldings[cashSym] = cashHoldings.clean()
        holdingsReport[currentStatementDate.toTransactionDate()] = HoldingsReport(preMaritalHoldings.toMap(), sharedHoldings.toMap())
        logger.info { "[$currentStatementDate] Summary of holdings: ${holdingsReport[currentStatementDate.toTransactionDate()]?.toStringDetailed()}"  }

        return ProcessMaritalTransactionsOutput(holdingsReport, transactionLog)
    }
}