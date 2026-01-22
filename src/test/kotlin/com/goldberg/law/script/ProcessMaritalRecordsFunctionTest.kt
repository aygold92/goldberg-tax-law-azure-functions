package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.*
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsFunction.Companion.add
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsFunction.Companion.getStatementDate
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsFunction.Companion.subtract
import com.goldberg.law.script.maritalinvestments.model.*
import com.goldberg.law.script.maritalinvestments.HoldingsReport
import com.goldberg.law.util.bd
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toStringDetailed
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.assertj.core.api.ObjectAssert
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.math.RoundingMode
import java.nio.file.Files
import java.nio.file.Paths
import kotlin.collections.mapOf

class ProcessMaritalRecordsFunctionTest {
    @Nested
    inner class LowLevelFunctionality {
        @Test
        fun testAddAndSubtract() {
            val map: MutableMap<InstrumentKey, BigDecimal> = mutableMapOf(SYMBOL_MMF to BigDecimal.ZERO)
            map.add(SYMBOL_MMF, 1.bd())

            assertThat(map).isEqualTo(mutableMapOf(SYMBOL_MMF to 1.bd()))

            map.subtract(SYMBOL_MMF, 1.bd())

            assertThat(map).isEqualTo(mutableMapOf(SYMBOL_MMF to BigDecimal.ZERO))

            map.add(SYMBOL_MMF, 10.bd())
            assertThat(map).isEqualTo(mutableMapOf(SYMBOL_MMF to BigDecimal.ZERO, SYMBOL_MMF to 10.bd()))
        }

        @Test
        fun testStatementDate() {
            assertThat(newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.WIRE_IN, 200.bd()).getStatementDate())
                .isEqualTo(fromWrittenDate("11/1/2020"))
            assertThat(newTransaction(fromWrittenDate("11/1/2020")!!, SYMBOL_MMF, VanguardTransactionType.WIRE_IN, 200.bd()).getStatementDate())
                .isEqualTo(fromWrittenDate("11/1/2020"))
            assertThat(newTransaction(fromWrittenDate("11/2/2020")!!, SYMBOL_MMF, VanguardTransactionType.WIRE_IN, 200.bd()).getStatementDate())
                .isEqualTo(fromWrittenDate("12/1/2020"))
        }
    }

    @Nested
    inner class SplitCashAccounts {
        @Test
        fun testSellAllThenDistributionReinvestment() {
            val transactionLog: List<TransactionLog> = listOf(
                // sweep in to set the cash symbol
                newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 100.bd()).log(null),
                // sell to 0, (200, 400) in MMF after
                newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.CORP_ACTION_REDEMPTION, 200.bd(), 100.bd()).log(.5.bd()),
                // distribute at .5 so (205, 405)
                newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.INTEREST, 10.bd()).log(.5.bd()),
                // reinvest at .5 we end up with (5,5) shares and (195, 395)
                newTransaction(DATE_2, SYMBOL_1, VanguardTransactionType.REINVESTMENT, 20.bd(), 10.bd()).log(.5.bd()),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactions(
                transactions = transactionLog.map { it.transaction },
                startingHoldings = HoldingsReport(
                    preMaritalHoldings = mapOf(
                        SYMBOL_MMF to 100.bd(),
                        SYMBOL_1 to 50.bd()
                    ),
                    sharedHoldings = mapOf(
                        SYMBOL_MMF to 300.bd(),
                        SYMBOL_1 to 50.bd()
                    ),
                ),
                marriageDate = DATE_1
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
                        preMaritalHoldings = mapOf(
                            SYMBOL_MMF to 195.bd(),
                            SYMBOL_1 to 5.bd()
                        ),
                        sharedHoldings = mapOf(
                            SYMBOL_MMF to 395.bd(),
                            SYMBOL_1 to 5.bd()
                        )
                    )),
                    transactionLog
                )
            )
        }

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

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactions(
                transactions = transactionLog.map { it.transaction },
                startingHoldings = HoldingsReport(
                    preMaritalHoldings = mapOf(SYMBOL_MMF to 100.bd()),
                    sharedHoldings = mapOf(),
                ),
                marriageDate = DATE_1
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
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
        fun testSomeSharedTransactions() {
            val transactionLog: List<TransactionLog> = listOf(
                newTransaction(DATE_0, HoldingSymbol.NO_SYM, VanguardTransactionType.WIRE_IN, 200.bd()).log(BigDecimal.ZERO),
                newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 200.bd()).log(null),
                // now 100 p, 200 m
                newTransaction(DATE_1, SYMBOL_MMF, VanguardTransactionType.SWEEP_OUT, 50.bd()).log(null),
                // now 2/3 go to marriage, so 1.6666667 and 3.33333
                newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 25.bd(), 5.bd()).log(".3333333333333333333333333".bd()),
                // now 2/3 go to marriage, so 8.33333 and 16.666667
                newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 25.bd(), 25.bd()).log(".3333333333333333333333333".bd()),

                // total is 18.3333 and 16.66667, so the ratio is 0.5238095238.  This means they sell 7.8571428571 and 7.1428571429
                newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 75.bd(), 15.bd()).log("0.52380952380952380951".bd()),
                // ratio of 0.5238095238 means they get 39.2857142857 and 35.7142857143
                newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 75.bd()).log(null),
                // they own 51.66667 and 3.3333, so the ratio is 0.9393939394, meaning they get 46.9696969697 and 3.03030303027
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log("0.93939393939393939395".bd()),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log("0.93939393939393939395".bd()),
                // ~170.666667,
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log("0.45223665223665223665".bd()),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactions(
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
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
                        preMaritalHoldings = mapOf(
                            SYMBOL_MMF to "146.976911976911976913".bd(),
                            SYMBOL_1 to "51.666666666666666667".bd(),
                            SYMBOL_2 to "10.47619047619047619".bd(),
                        ),
                        sharedHoldings = mapOf(
                            SYMBOL_MMF to "178.023088023088023087".bd(),
                            SYMBOL_1 to "3.333333333333333333".bd(),
                            SYMBOL_2 to "9.52380952380952381".bd(),
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
                // now (52, 4)
                newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.BUY, 75.bd(), 6.bd()).log(".3333333333333333333333333".bd()),
                // now (20,20)
                newTransaction(DATE_1, SYMBOL_2, VanguardTransactionType.BUY, 75.bd(), 30.bd()).log(".3333333333333333333333333".bd()),

                // now (11, 11) distributes cash (40, 40) == (90, 140)
                newTransaction(DATE_2, SYMBOL_2, VanguardTransactionType.SELL_EXCHANGE, 80.bd(), 18.bd()).log(.5.bd()),
                newTransaction(DATE_2, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 80.bd()).log(null),

                // after distributions we will have (142, 144) cash
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 28.bd()).log("0.9285714285714285714285714".bd()),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 28.bd()).log("0.9285714285714285714285714".bd()),
                // withdraw 71 and 72
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 143.bd()).log("0.4965034965034965034965035".bd()),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactions(
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
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
                        preMaritalHoldings = mapOf(
                            SYMBOL_MMF to 71.bd(),
                            SYMBOL_1 to 52.bd(),
                            SYMBOL_2 to 11.bd(),
                        ),
                        sharedHoldings = mapOf(
                            SYMBOL_MMF to 72.bd(),
                            SYMBOL_1 to 4.bd(),
                            SYMBOL_2 to 11.bd(),
                        )
                    )),
                    transactionLog
                )
            )
        }
    }

    @Nested
    inner class SharedCash {
        @Test
        fun testSellAllThenDistributionReinvestment() {
            val transactionLog: List<TransactionLog> = listOf(
                // sweep in to set the cash symbol
                newTransaction(DATE_0, SYMBOL_MMF, VanguardTransactionType.SWEEP_IN, 100.bd()).log(null),
                newTransaction(DATE_0, SYMBOL_1, VanguardTransactionType.CORP_ACTION_REDEMPTION, 200.bd(), 100.bd()).log(.5.bd()),
                newTransaction(DATE_1, SYMBOL_1, VanguardTransactionType.INTEREST, 10.bd()).log(0.bd()),
                // reinvest at .5 we end up with 5 shares each
                newTransaction(DATE_2, SYMBOL_1, VanguardTransactionType.REINVESTMENT, 20.bd(), 10.bd()).log(.5.bd()),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactionsSharedCash(
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
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
                        preMaritalHoldings = mapOf(
                            SYMBOL_MMF to BigDecimal.ZERO,
                            SYMBOL_1 to 5.bd()
                        ),
                        sharedHoldings = mapOf(
                            SYMBOL_MMF to 590.bd(),
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
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log(BigDecimal.ZERO),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log(BigDecimal.ZERO),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(BigDecimal.ZERO),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactionsSharedCash(
                transactions = transactionLog.map { it.transaction },
                startingHoldings = HoldingsReport(
                    preMaritalHoldings = mapOf(SYMBOL_MMF to 100.bd()),
                    sharedHoldings = mapOf(),
                ),
                marriageDate = DATE_1
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
                        preMaritalHoldings = mapOf(
                            SYMBOL_MMF to BigDecimal.ZERO,
                            SYMBOL_1 to 5.bd(),
                            SYMBOL_2 to 10.bd(),
                        ),
                        sharedHoldings = mapOf(
                            SYMBOL_MMF to 225.bd(),
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
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.DIVIDEND, 25.bd()).log(BigDecimal.ZERO),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.CAPITAL_GAIN_LT, 25.bd()).log(BigDecimal.ZERO),
                newTransaction(DATE_3, SYMBOL_1, VanguardTransactionType.WITHDRAWAL, 50.bd()).log(BigDecimal.ZERO),
            )

            val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactionsSharedCash(
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
            )

            assertThat(result).bigDecimalCompare().isEqualTo(
                ProcessMaritalTransactionsOutput(
                    holdingsReport = mapOf("11/1/2020" to HoldingsReport(
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


    @ParameterizedTest
    @ValueSource(booleans = [true, false])
    fun runFromFile(useSplitCashAccounts: Boolean) {
        val csv = readFileRelative("investments.csv")
        val result = ProcessMaritalTransactionsFunction(CSV_PARSER).processMaritalTransactions(
            ProcessMaritalTransactionsInput(
                transactionsCsv = csv,
                startingHoldings = HoldingsReport(
                    preMaritalHoldings = mapOf(
                        HoldingSymbol("VMFXX") to 127079.95.bd(),
                        HoldingSymbol("VBTLX") to 1111.119.bd(),
                        HoldingSymbol("VTSAX") to 2507.706.bd(),
                        HoldingSymbol("VTWAX") to 885.464.bd(),
                    ),
                    sharedHoldings = mapOf()
                ),
                marriageDate = "11/2/2020",
                useSplitCashAccounts = useSplitCashAccounts
            )
        )

        println(result.toStringDetailed())
    }

    private fun <T> ObjectAssert<T>.bigDecimalCompare() = this.usingRecursiveComparison().ignoringFieldsMatchingRegexes(".*\\\$delegate").withComparatorForType(BIG_DECIMAL_COMPARATOR, BigDecimal::class.java)

    companion object {
        val BIG_DECIMAL_COMPARATOR = Comparator<BigDecimal> { a, b ->
            if (a == b) 0
            else if (a == null) -1
            else if (b == null) 1
            else a.setScale(10, RoundingMode.HALF_EVEN)
                .compareTo(b.setScale(10, RoundingMode.HALF_EVEN))
        }
        val CSV_PARSER = CsvParser()

        fun Int.bd() = BigDecimal(this)
        fun String.bd() = BigDecimal(this)

        val PATH_TO_CLASS = "/" + ProcessMaritalRecordsFunctionTest::class.java.packageName.replace('.', '/')

        fun readFileRelative(filename: String): String =
            Files.readString(Paths.get(javaClass.getResource("$PATH_TO_CLASS/$filename")!!.toURI()))
    }
}