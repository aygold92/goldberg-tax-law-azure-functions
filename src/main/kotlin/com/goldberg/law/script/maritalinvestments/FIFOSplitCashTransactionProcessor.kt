package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.util.toStringDetailed
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*

class FIFOSplitCashTransactionProcessor(
    transactions: List<InvestmentTransaction>,
    startingHoldings: HoldingsReport,
    marriageDate: Date
): MaritalTransactionProcessor(transactions, startingHoldings, marriageDate) {
    val cashQueue: CashQueue = CashQueue()

    override fun initCashHolding(cashSym: InstrumentKey) {
        val (startingAmountPreMarital, startingAmountMarital) = Pair(startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO, startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)
        val startingRatio = calculateRatio(startingAmountPreMarital, startingAmountMarital)
        cashQueue.add(startingAmountPreMarital + startingAmountMarital, startingRatio)
    }
    override fun getCurrentCashTotals(isPostMarriage: Boolean): Pair<BigDecimal, BigDecimal> = cashQueue.totalByClassification()

    override fun consumeCash(amount: BigDecimal, isPostMarriage: Boolean): BigDecimal = cashQueue.poll(amount).let { (preMaritalCash, sharedCash) ->
        calculateRatio(preMaritalCash, sharedCash)
    }

    override fun addCash(amount: BigDecimal, preMaritalPercent: BigDecimal, isPostMarriage: Boolean) = cashQueue.add(amount, preMaritalPercent)
}