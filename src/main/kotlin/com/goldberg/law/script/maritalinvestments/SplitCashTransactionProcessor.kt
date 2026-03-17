package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InvestmentTransaction
import com.goldberg.law.util.clean
import java.math.BigDecimal
import java.util.*

class SplitCashTransactionProcessor(
    transactions: List<InvestmentTransaction>,
    startingHoldings: HoldingsReport,
    marriageDate: Date
): MaritalTransactionProcessor(transactions, startingHoldings, marriageDate) {
    lateinit var preMaritalCash: BigDecimal
    lateinit var sharedCash: BigDecimal

    override fun initCashHolding(cashSym: InstrumentKey) {
        preMaritalCash = startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO
        sharedCash = startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO
    }

    override fun getCurrentCashTotals(isPostMarriage: Boolean): Pair<BigDecimal, BigDecimal> = Pair(preMaritalCash, sharedCash)

    override fun consumeCash(amount: BigDecimal, isPostMarriage: Boolean): BigDecimal {
        val preMaritalCashPercent = calculateRatio(preMaritalCash, sharedCash)
        val preMaritalAmount = amount.multiplyScale(preMaritalCashPercent)
        preMaritalCash = (preMaritalCash - preMaritalAmount).clean()
        sharedCash = (sharedCash - (amount - preMaritalAmount)).clean()
        return preMaritalCashPercent
    }

    override fun addCash(amount: BigDecimal, preMaritalPercent: BigDecimal, isPostMarriage: Boolean) = amount.multiplyScale(preMaritalPercent).let { preMaritalAmount ->
        preMaritalCash += preMaritalAmount
        sharedCash += amount - preMaritalAmount
    }
}