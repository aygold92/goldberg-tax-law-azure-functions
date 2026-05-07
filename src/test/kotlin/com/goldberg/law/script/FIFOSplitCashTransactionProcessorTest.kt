package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.FIFOSplitCashTransactionProcessor
import com.goldberg.law.script.maritalinvestments.HoldingsReport
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsOutput
import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.TransactionClassification
import com.goldberg.law.script.maritalinvestments.model.TransactionLog
import com.goldberg.law.script.maritalinvestments.model.VanguardTransactionType
import com.goldberg.law.util.bd
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class FIFOSplitCashTransactionProcessorTest {
    @Test
    fun testNoSharedTransactions() {
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
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(BigDecimal.ONE),
        )

        val result = FIFOSplitCashTransactionProcessor(
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
                        SYMBOL_MMF to 225.bd(),
                        SYMBOL_1 to 5.bd(),
                        SYMBOL_2 to 10.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to BigDecimal.ZERO,
                        SYMBOL_1 to BigDecimal.ZERO,
                        SYMBOL_2 to BigDecimal.ZERO,
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testNoSharedTransactionsBecauseFIFO() {
        val transactionLog: List<TransactionLog> = listOf(
            // now 100 p, 200 m
            newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 200.bd()).log(BigDecimal.ZERO),
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 200.bd()).log(null),
            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.SWEEP_OUT, 50.bd()).log(null),
            // all 25 go to p, 75 remaining
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 25.bd(), 5.bd()).log(1.bd()),
            // all 25 go to p, 50 remaining
            newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 25.bd(), 25.bd()).log(1.bd()),

            // all proceeds go to premarital at the end of queue (50p,200m,75p)
            newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 75.bd(), 15.bd()).log(1.bd()),
            newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 75.bd()).log(null),
            // both distributed to premarital cash (50p,200m,75p,50p)
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log(1.bd()),
            newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log(1.bd()),
            // all premarital
            newTransaction(DATE_3, HoldingSymbol.NO_SYM, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(1.bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
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
                        SYMBOL_MMF to 125.bd(),
                        SYMBOL_1 to 55.bd(),
                        SYMBOL_2 to 20.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 200.bd(),
                        SYMBOL_1 to 0.bd(),
                        SYMBOL_2 to 0.bd(),
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testSomeSharedTransactionsEven() {
        val transactionLog: List<TransactionLog> = listOf(
            // now 100 p, 200 m
            newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 200.bd()).log(BigDecimal.ZERO),
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 200.bd()).log(null),

            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.SWEEP_OUT, 150.bd()).log(null),
            // now (25p, 200m)
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 75.bd(), 6.bd()).log(1.bd()),
            // now (150m)
            newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 75.bd(), 30.bd()).log(".3333333333333333333333333".bd()),

            // now (11, 11) distributes cash (40, 40) == [{150,0}, {80,.5}]
            newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 80.bd(), 18.bd()).log(.5.bd()),
            newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 80.bd()).log(null),

            // after distributions [{150,0}, {80,.5}, {28, .5}, {28, .5}]
            newTransaction(DATE_3, SYMBOL_2, VanguardTransactionType.DIVIDEND, 28.bd()).log(.5.bd()),
            newTransaction(DATE_3, SYMBOL_2, VanguardTransactionType.CAPITAL_GAIN_LT, 28.bd()).log(.5.bd()),
            // withdraw all from marital
            newTransaction(DATE_3, HoldingSymbol.NO_SYM, VanguardTransactionType.WITHDRAWAL, 143.bd()).log(0.bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(
                    SYMBOL_MMF to 100.bd(),
                    SYMBOL_1 to 50.bd(),
                    SYMBOL_2 to 10.bd()
                ),
                sharedHoldings = mapOf()
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to 68.bd(),
                        SYMBOL_1 to 56.bd(),
                        SYMBOL_2 to 11.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 75.bd(),
                        SYMBOL_1 to 0.bd(),
                        SYMBOL_2 to 11.bd(),
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testMixedFIFOGrouping() {
        val transactionLog: List<TransactionLog> = listOf(
            // FIFO starts with 200 at half going to each, so 50 remaining
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.BUY, 150.bd(), 10.bd()).log(.5.bd()),
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 150.bd()).log(null),
            // SYMBOL_1 has 15p/5m, new Queue is [{50,.5},{80,.75}]
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.DIVIDEND, 80.bd()).log(.75.bd()),
            // 50 comes at .5 and 50 comes at .75, so total is 62.5/37.5.  Remaining queue is {30, .75}
            newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.BUY, 100.bd(), 30.bd()).log(".625".bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(
                    SYMBOL_MMF to 100.bd(),
                    SYMBOL_1 to 10.bd(),
                ),
                sharedHoldings = mapOf(
                    SYMBOL_MMF to 100.bd()
                )
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to 22.5.bd(),
                        SYMBOL_1 to 15.bd(),
                        SYMBOL_2 to 18.75.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 7.5.bd(),
                        SYMBOL_1 to 5.bd(),
                        SYMBOL_2 to 11.25.bd(),
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testDividendsWithAndWithoutMatchingReInvestments() {
        val transactionLog: List<TransactionLog> = listOf(
            // Cash queue starts with {100,.6}
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 150.bd()).log(null),
            // 15 distributed to each.  Since the dividend does not enter the cash queue, it is used directly instead of using .6
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.DIVIDEND, 80.bd()).log(.5.bd()),
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.REINVESTMENT, 80.bd(), 30.bd()).log(.5.bd()),
            // 3p to 1m, again does not draw from cash queue
            newTransaction(DATE_0, SYMBOL_2, VanguardTransactionType.REINVESTMENT, 80.bd(), 4.bd()).log(.75.bd()),
            newTransaction(DATE_0, SYMBOL_2, VanguardTransactionType.DIVIDEND, 80.bd()).log(.75.bd()),
            // 2 distributed to each
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.REINVESTMENT, 80.bd(), 4.bd()).log(.5.bd()),
            // this one enters the cash queue [{100, .6}, {80,.75})
            newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.DIVIDEND, 80.bd()).log(.75.bd()),
            // even though the order is mixed, it uses the matching transactions
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.DIVIDEND, 80.bd()).log(.5.bd()),
            // 75/45 cash, {60,.75} remaining
            newTransaction(DATE_2, SYMBOL_1, VanguardTransactionType.BUY, 120.bd(), 10.bd()).log(.625.bd()),

        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(
                    SYMBOL_MMF to 60.bd(),
                    SYMBOL_1 to 10.bd(),
                    SYMBOL_2 to 3.bd(),
                ),
                sharedHoldings = mapOf(
                    SYMBOL_MMF to 40.bd(),
                    SYMBOL_1 to 10.bd(),
                    SYMBOL_2 to 1.bd(),
                )
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to 45.bd(),
                        SYMBOL_1 to 33.25.bd(),
                        SYMBOL_2 to 6.bd(),
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 15.bd(),
                        SYMBOL_1 to 30.75.bd(),
                        SYMBOL_2 to 2.bd(),
                    )
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testMultipleStatementPeriods() {
        // Exercises the mid-loop holdingsReport snapshot taken when crossing a statement boundary.
        // All DATE_0/DATE_1 transactions fall in October (statement date 2020-11-01).
        // dateNov falls in November (statement date 12/1/2020), triggering the snapshot.
        val dateNov = fromWrittenDate("11/5/2020")!!

        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 50.bd()).log(null),
            // starting cash is (200, .5); buy uses all of it: 100 PM, 100 M -> ratio 0.5
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.BUY, 200.bd(), 40.bd()).log(.5.bd()),
            // crosses statement boundary here; snapshot at 2020-11-01 captures cash=0, SYMBOL_1=20PM/20M
            // sell all 40 shares at 0.5 ratio, proceeds (100, .5) go into cash queue
            newTransaction(dateNov, SYMBOL_1, VanguardTransactionType.CORP_ACTION_REDEMPTION, 100.bd(), 40.bd()).log(.5.bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(SYMBOL_MMF to 100.bd()),
                sharedHoldings = mapOf(SYMBOL_MMF to 100.bd()),
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf(
                    "2020-11-01" to HoldingsReport(
                        preMaritalHoldings = mapOf(SYMBOL_MMF to 0.bd(), SYMBOL_1 to 20.bd()),
                        sharedHoldings = mapOf(SYMBOL_MMF to 0.bd(), SYMBOL_1 to 20.bd()),
                    ),
                    "2020-12-01" to HoldingsReport(
                        preMaritalHoldings = mapOf(SYMBOL_MMF to 50.bd(), SYMBOL_1 to 0.bd()),
                        sharedHoldings = mapOf(SYMBOL_MMF to 50.bd(), SYMBOL_1 to 0.bd()),
                    ),
                ),
                transactionLog
            )
        )
    }

    @Test
    fun testSellToZeroThenUnpairedDistributionUsesLastKnownRatio() {
        // When a symbol is sold to 0, lastKnownRatios retains the ratio so that
        // a subsequent unpaired DISTRIBUTION for the same symbol can still be split correctly.
        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 50.bd()).log(null),
            // SYMBOL_1 is 60PM/40M = 0.6 ratio; sell all 100 shares
            newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.CORP_ACTION_REDEMPTION, 100.bd(), 100.bd()).log(.6.bd()),
            // SYMBOL_1 holdings are now 0/0; fallback to lastKnownRatio 0.6
            newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.DIVIDEND, 50.bd()).log(.6.bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(SYMBOL_MMF to 100.bd(), SYMBOL_1 to 60.bd()),
                sharedHoldings = mapOf(SYMBOL_1 to 40.bd()),
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        // cash after sell: starting (100, 1.0) + proceeds (100, 0.6) + distribution (50, 0.6)
        // PM = 100 + 60 + 30 = 190, M = 0 + 40 + 20 = 60
        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(SYMBOL_MMF to 190.bd(), SYMBOL_1 to 0.bd()),
                    sharedHoldings = mapOf(SYMBOL_MMF to 60.bd(), SYMBOL_1 to 0.bd()),
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testCashSymDistributionWithAndWithoutReinvestment() {
        // An unpaired DISTRIBUTION of cashSym and a paired DISTRIBUTION+RE_INVESTMENT of cashSym
        // should both add the same amount to the cash queue at the current cash ratio.
        // DATE_0: unpaired DIVIDEND of cashSym — DISTRIBUTION handler adds directly to cash queue.
        // DATE_1: paired DIVIDEND+REINVESTMENT of cashSym — DISTRIBUTION is skipped,
        //         RE_INVESTMENT adds to cash queue instead. Net effect is identical.
        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 50.bd()).log(null),
            // unpaired: adds (20, 0.6) to cash queue directly
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.DIVIDEND, 20.bd()).log(.6.bd()),
            // paired: DISTRIBUTION is skipped, RE_INVESTMENT adds (20, 0.6) instead
            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.DIVIDEND, 20.bd()).log(.6.bd()),
            newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.REINVESTMENT, 20.bd()).log(.6.bd()),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(SYMBOL_MMF to 60.bd()),
                sharedHoldings = mapOf(SYMBOL_MMF to 40.bd()),
            ),
            marriageDate = DATE_BEFORE
        ).processMaritalTransactions()

        // starting (100, 0.6) + unpaired dividend (20, 0.6) + paired reinvestment (20, 0.6)
        // total cash = 140, PM = 140 * 0.6 = 84, M = 140 * 0.4 = 56
        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(SYMBOL_MMF to 84.bd()),
                    sharedHoldings = mapOf(SYMBOL_MMF to 56.bd()),
                )),
                transactionLog
            )
        )
    }

    @Test
    fun testDepositClassificationOverride() {
        // A pre-marriage deposit with Marital override goes to marital cash.
        // A post-marriage deposit with PreMarital override goes to pre-marital cash.
        // Both bypass the normal date-based classification.
        val transactionLog: List<TransactionLog> = listOf(
            newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 50.bd()).log(null),
            // DATE_0 is before marriageDate (DATE_1), so normally pre-marital — override to marital
            newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 100.bd(), override = TransactionClassification.Marital).log(BigDecimal.ZERO),
            // DATE_2 is after marriageDate (DATE_1), so normally marital — override to pre-marital
            newTransaction(DATE_2, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 50.bd(), override = TransactionClassification.PreMarital).log(BigDecimal.ONE),
        )

        val result = FIFOSplitCashTransactionProcessor(
            transactions = transactionLog.map { it.transaction },
            startingHoldings = HoldingsReport(
                preMaritalHoldings = mapOf(SYMBOL_MMF to 10.bd()),
                sharedHoldings = mapOf(),
            ),
            marriageDate = DATE_1
        ).processMaritalTransactions()

        // cash: starting (10, 1.0) + marital override (100, 0.0) + pre-marital override (50, 1.0)
        // PM = 10 + 0 + 50 = 60, M = 0 + 100 + 0 = 100
        assertThat(result).bigDecimalCompare().isEqualTo(
            ProcessMaritalTransactionsOutput(
                holdingsReport = mapOf("2020-11-01" to HoldingsReport(
                    preMaritalHoldings = mapOf(SYMBOL_MMF to 60.bd()),
                    sharedHoldings = mapOf(SYMBOL_MMF to 100.bd()),
                )),
                transactionLog
            )
        )
    }
}