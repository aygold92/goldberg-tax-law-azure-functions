package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.CsvParser
import com.goldberg.law.script.maritalinvestments.HoldingsReport
import com.goldberg.law.script.maritalinvestments.MaritalTransactionProcessor.Companion.add
import com.goldberg.law.script.maritalinvestments.MaritalTransactionProcessor.Companion.getStatementDate
import com.goldberg.law.script.maritalinvestments.MaritalTransactionProcessor.Companion.subtract
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsFunction
import com.goldberg.law.script.maritalinvestments.ProcessMaritalTransactionsInput
import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.VanguardTransactionType
import com.goldberg.law.util.bd
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toStringDetailed
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal
import java.nio.file.Files
import java.nio.file.Paths

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

    @ParameterizedTest
    @ValueSource(strings = ["SplitCash", "SharedCash", "FIFO"])
    fun runFromFile(processorType: ProcessMaritalTransactionsFunction.MaritalTransactionProcessorType) {
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
                processorType = processorType
            )
        )

        println(result.toStringDetailed())
    }


    companion object {
        val CSV_PARSER = CsvParser()

        val PATH_TO_CLASS = "/" + ProcessMaritalRecordsFunctionTest::class.java.packageName.replace('.', '/')

        fun readFileRelative(filename: String): String =
            Files.readString(Paths.get(javaClass.getResource("$PATH_TO_CLASS/$filename")!!.toURI()))
    }
}