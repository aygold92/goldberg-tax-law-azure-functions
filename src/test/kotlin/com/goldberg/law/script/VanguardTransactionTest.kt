package com.goldberg.law.script

import com.goldberg.law.script.maritalinvestments.CsvParser
import com.goldberg.law.script.maritalinvestments.model.TreasuryCoupon
import com.goldberg.law.script.maritalinvestments.model.VanguardTransaction
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
}