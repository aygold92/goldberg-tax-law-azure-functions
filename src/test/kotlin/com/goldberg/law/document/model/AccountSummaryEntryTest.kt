package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.output.AccountSummaryEntry
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class AccountSummaryEntryTest {
    @Test
    fun testToCsvBasic() {
        assertThat(AccountSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            emptyList(), emptyList()
        ).toCsv()).isEqualTo(
            "\"1234567890\",WF Bank,6/5/2020,9/2/2020,\"[]\",\"[]\""
        )
    }
    @Test
    fun testToCsvWithMissingAndSus() {
        assertThat(AccountSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            listOf("7/2020", "8/2020"), listOf(fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!)
        ).toCsv()).isEqualTo(
            "\"1234567890\",WF Bank,6/5/2020,9/2/2020,\"[7/2020, 8/2020]\",\"[6/5/2020, 9/2/2020]\""
        )
    }
}