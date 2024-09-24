package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE_DATE
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.asCurrency
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatementSummaryTest {
    @Test
    fun testNormal() {
        assertThat(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                FIXED_STATEMENT_DATE_DATE,
                500.asCurrency(),
                1000.asCurrency(),
                500.asCurrency(),
                2,
                setOf(BATES_STAMP, "AG-123457"),
                FILENAME,
                setOf(1,2),
                false,
                listOf()
        ).toCsv()).isEqualTo(
            """
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",4/7/2020,500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME","[1, 2]",Verified,
            """.trimIndent()
        )
    }

    @Test
    fun testSuspicious() {
        assertThat(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                FIXED_STATEMENT_DATE_DATE,
                500.asCurrency(),
                1000.asCurrency(),
                500.asCurrency(),
                2,
                setOf(BATES_STAMP, "AG-123457"),
                FILENAME,
                setOf(1,2),
                true,
                listOf(TransactionHistoryRecord.SuspiciousReasons.NO_DATE, BankStatement.SuspiciousReasons.INCORRECT_DATES)
        ).toCsv()).isEqualTo(
            """
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",4/7/2020,500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME","[1, 2]",ERROR,"[${TransactionHistoryRecord.SuspiciousReasons.NO_DATE}, ${BankStatement.SuspiciousReasons.INCORRECT_DATES}]"
            """.trimIndent()
        )
    }
}