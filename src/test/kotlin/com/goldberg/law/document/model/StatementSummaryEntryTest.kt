package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.pdf.model.StatementType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class StatementSummaryEntryTest {
    @Test
    fun testToCsvBasic() {
        assertThat(StatementSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            emptyList(), emptyList()
        ).toCsv()).isEqualTo(
            "\"1234567890\",WF_BANK,6/5/2020,9/2/2020,\"[]\",\"[]\""
        )
    }
    @Test
    fun testToCsvWithMissingAndSus() {
        assertThat(StatementSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            listOf("7/2020", "8/2020"), listOf(fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!)
        ).toCsv()).isEqualTo(
            "\"1234567890\",WF_BANK,6/5/2020,9/2/2020,\"[7/2020, 8/2020]\",\"[6/5/2020, 9/2/2020]\""
        )
    }
}