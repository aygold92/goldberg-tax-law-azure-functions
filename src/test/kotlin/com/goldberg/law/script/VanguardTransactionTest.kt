package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.CsvParser
import com.goldberg.law.script.maritalinvestments.model.TransactionClassification
import com.goldberg.law.script.maritalinvestments.model.HoldingSymbol
import com.goldberg.law.script.maritalinvestments.model.TreasuryCoupon
import com.goldberg.law.script.maritalinvestments.model.VanguardTransaction
import com.goldberg.law.script.maritalinvestments.model.VanguardTransactionType
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.bd
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class VanguardTransactionTest {
    @Test
    fun testToCsv() {
        val line = """
            12/5/2024,12/5/2024,—,U S TREASURY BILL CPN MTD 2024-12-05 DTD 2024-06-06,Corp Action (Redemption),CASH,"25,000.00",—,—,"24,348.47"
            7/31/2025,7/31/2025,—,U S TREASURY NOTE CPN 4.25000 % MTD 2026-01-31 DTD 2024-01-31,Interest,CASH,—,—,—,531.25
        """.trimIndent()
        val records = CsvParser().parse(line).map { VanguardTransaction.fromCsvLine(it) }
        assertThat(records[0].symbol).isEqualTo(TreasuryCoupon("U S TREASURY BILL", null, "2024-12-05", "2024-06-06"))
        assertThat(records[1].symbol).isEqualTo(TreasuryCoupon("U S TREASURY NOTE", null, "2026-01-31", "2024-01-31"))
        println(records.joinToString("\n"))
    }

    @Test
    fun testOverrides() {
        val line = """
            12/10/2021,12/10/2021,—,CASH,Wire In,CASH,—,—,—,"328,063.30",PreMarital
            12/20/2021,12/17/2021,VBTLX,Vanguard Total Bond Market Index Fund Admiral Shares,Buy,CASH,31.111,11.25,Free,-350.00,Marital
            12/17/2021,12/17/2021,VTWAX,Vanguard Total World Stock Index Fund Admiral Shares,Dividend,CASH,—,—,—,83.31
        """.trimIndent()

        val records = CsvParser().parse(line).map { VanguardTransaction.fromCsvLine(it) }
        assertThat(records[0]).isEqualTo(VanguardTransaction(
            fromWrittenDate("12/10/2021")!!,
            fromWrittenDate("12/10/2021")!!,
            HoldingSymbol.NO_SYM,
            "CASH",
            VanguardTransactionType.WIRE_IN,
            "CASH",
            null,
            null,
            null,
            328063.3.asCurrency(),
            TransactionClassification.PreMarital
        ))
        assertThat(records[1]).isEqualTo(VanguardTransaction(
            fromWrittenDate("12/20/2021")!!,
            fromWrittenDate("12/17/2021")!!,
            HoldingSymbol("VBTLX"),
            "Vanguard Total Bond Market Index Fund Admiral Shares",
            VanguardTransactionType.BUY,
            "CASH",
            31.111.bd(),
            11.25.asCurrency(),
            ZERO,
            350.asCurrency(),
            TransactionClassification.Marital
        ))
        assertThat(records[2]).isEqualTo(VanguardTransaction(
            fromWrittenDate("12/17/2021")!!,
            fromWrittenDate("12/17/2021")!!,
            HoldingSymbol("VTWAX"),
            "Vanguard Total World Stock Index Fund Admiral Shares",
            VanguardTransactionType.DIVIDEND,
            "CASH",
            null,
            null,
            null,
            83.31.asCurrency(),
            null
        ))
        println(records.joinToString("\n"))
    }
}