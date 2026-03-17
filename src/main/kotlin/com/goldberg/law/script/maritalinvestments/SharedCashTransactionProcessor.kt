package com.goldberg.law.script.maritalinvestments

import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InvestmentTransaction
import com.goldberg.law.util.clean
import java.math.BigDecimal
import java.util.*

class SharedCashTransactionProcessor(
    transactions: List<InvestmentTransaction>,
    startingHoldings: HoldingsReport,
    marriageDate: Date): MaritalTransactionProcessor(transactions, startingHoldings, marriageDate) {
    lateinit var cashHoldings: BigDecimal

    override fun initCashHolding(cashSym: InstrumentKey) {
        cashHoldings = (startingHoldings.preMaritalHoldings[cashSym] ?: BigDecimal.ZERO) + (startingHoldings.sharedHoldings[cashSym] ?: BigDecimal.ZERO)
    }

    override fun getCurrentCashTotals(isPostMarriage: Boolean): Pair<BigDecimal, BigDecimal> =
        if (isPostMarriage) Pair(BigDecimal.ZERO, cashHoldings)
        else Pair(BigDecimal.ZERO, cashHoldings)

    override fun consumeCash(amount: BigDecimal, isPostMarriage: Boolean): BigDecimal {
        cashHoldings = (cashHoldings - amount).clean()
        return if (isPostMarriage) BigDecimal.ZERO else BigDecimal.ONE
    }

    override fun addCash(amount: BigDecimal, preMaritalPercent: BigDecimal, isPostMarriage: Boolean) {
        cashHoldings = (cashHoldings + amount).clean()
    }
}