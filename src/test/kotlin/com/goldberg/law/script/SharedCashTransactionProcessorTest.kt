package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.HoldingsReport
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsOutput
import com.goldberg.law.script.maritalinvestments.SharedCashTransactionProcessor
import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.TransactionLog
import com.goldberg.law.script.maritalinvestments.model.VanguardTransactionType
import com.goldberg.law.util.bd
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class SharedCashTransactionProcessorTest {
    @Test
    fun testSellAllThenDistributionReinvestment() {
        val transactionLog: List<TransactionLog> = listOf(
            // sweep in to set the cash symbol
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 100.bd()).log(null),
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.CORP_ACTION_REDEMPTION, 200.bd(), 100.bd()).log(.5.bd()),
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.INTEREST, 10.bd()).log(.5.bd()),
            // reinvest at .5 we end up with 5 shares each
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.REINVESTMENT, 10.bd(), 10.bd()).log(.5.bd()),
        )

        val result = SharedCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(
                    SYMBOL_MMF to 400.bd(),
                    SYMBOL_1 to 50.bd()
                ),
                sharedHoldings = mapOf(
                    SYMBOL_1 to 50.bd()
                ),
            ),
            marriageDate = DATE_0
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to BigDecimal.ZERO,
                        SYMBOL_1 to 5.bd()
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 600.bd(),
                        SYMBOL_1 to 5.bd()
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testNoNewPurchases() {
        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 100.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 100.bd()).log(null),
            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.SWEEP_OUT, 50.bd()).log(null),
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 25.bd(), 5.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 25.bd(), 25.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 75.bd(), 15.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 75.bd()).log(null),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log(BigDecimal.ONE),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(BigDecimal.ZERO),
        )

        val result = SharedCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(SYMBOL_MMF to 100.bd()),
                sharedHoldings = mapOf(),
            ),
            marriageDate = DATE_1
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to BigDecimal.ZERO,
                        SYMBOL_1 to 5.bd(),
                        SYMBOL_2 to 10.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 225.bd(),
                        SYMBOL_1 to BigDecimal.ZERO,
                        SYMBOL_2 to BigDecimal.ZERO,
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testSomeNewPurchases() {
        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 200.bd()).log(BigDecimal.ZERO),
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 200.bd()).log(null),
            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.SWEEP_OUT, 50.bd()).log(null),
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 25.bd(), 5.bd()).log(BigDecimal.ZERO),
            newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 25.bd(), 10.bd()).log(BigDecimal.ZERO),

            // total is 10 and 10, so the ratio is 0.5
            newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 75.bd(), 15.bd()).log(.5.bd()),
            newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 75.bd()).log(null),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log(".9090909090909090909090909090909091".bd()),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log(".9090909090909090909090909090909091".bd()),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(BigDecimal.ZERO),
        )

        val result = SharedCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(
                    SYMBOL_MMF to 100.bd(),
                    SYMBOL_1 to 50.bd(),
                    SYMBOL_2 to 10.bd()
                ),
                sharedHoldings = mapOf(),
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to 0.bd(),
                        SYMBOL_1 to 50.bd(),
                        SYMBOL_2 to 2.5.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 325.bd(),
                        SYMBOL_1 to 5.bd(),
                        SYMBOL_2 to 2.5.bd(),
                    )
                )),
                transactionLog
            )
        )
    }
}