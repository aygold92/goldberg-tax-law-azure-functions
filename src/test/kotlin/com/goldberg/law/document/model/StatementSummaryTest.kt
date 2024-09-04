package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatementSummaryTest {
    @Test
    fun testNormal() {
        assertThat(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                500.0,
                1000.0,
                500.0,
                2,
                setOf(BATES_STAMP, "AG-123457"),
                FILENAME,
                false,
                listOf()
        ).toCsv()).isEqualTo(
            """
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME",Verified,
            """.trimIndent()
        )
    }

    @Test
    fun testSuspicious() {
        assertThat(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                500.0,
                1000.0,
                500.0,
                2,
                setOf(BATES_STAMP, "AG-123457"),
                FILENAME,
                true,
                listOf(TransactionHistoryRecord.SuspiciousReasons.NO_DATE, BankStatement.SuspiciousReasons.INCORRECT_DATES)
        ).toCsv()).isEqualTo(
            """
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME",ERROR,"[${TransactionHistoryRecord.SuspiciousReasons.NO_DATE}, ${BankStatement.SuspiciousReasons.INCORRECT_DATES}]"
            """.trimIndent()
        )
    }
}