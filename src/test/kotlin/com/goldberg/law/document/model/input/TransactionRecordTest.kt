package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionRecordTest {
    @Test
    fun testStatementDateNormal() {
        assertThat(TransactionTableAmountRecord("6/1", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_NORMAL, BASIC_TH_PAGE_METADATA))
            .isEqualTo(newHistoryRecord(date = normalizeDate("6 1 2020")))
    }

    @Test
    fun testStatementDateEnd() {
        assertThat(TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_END, BASIC_TH_PAGE_METADATA))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2020")))
    }

    @Test
    fun testStatementDateBeginningAdjusted() {
        assertThat(TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2019")))
    }

    @Test
    fun testStatementDateBeginningNotAdjusted() {
        assertThat(TransactionTableAmountRecord("1/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA))
            .isEqualTo(newHistoryRecord(date = normalizeDate("1 5 2020")))
    }


    companion object {
        val STATEMENT_DATE_NORMAL = fromWrittenDate("6 5 2020")
        val STATEMENT_DATE_BEGINNING = fromWrittenDate("1 15 2020")
        val STATEMENT_DATE_END = fromWrittenDate("12 15 2020")
        const val DESCRIPTION = "test"
        val AMOUNT = 500.0.asCurrency()
    }
}