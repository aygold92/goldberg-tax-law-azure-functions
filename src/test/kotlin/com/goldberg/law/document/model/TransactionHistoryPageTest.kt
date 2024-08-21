package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryPageTest {
    @Test
    fun testNonSuspiciousPage() {
        assertThat(BASIC_TH_PAGE.isSuspicious()).isFalse()
    }

    @Test
    fun testSuspiciousPageNullFields() {
        assertThat(TransactionHistoryRecord().isSuspicious).isTrue()
        assertThat(BASIC_TH_PAGE.copy(statementDate = null).isSuspicious()).isTrue()
        assertThat(BASIC_TH_PAGE.copy(statementPageNum = null).isSuspicious()).isTrue()
    }

    @Test
    fun testSuspiciousPageRecord() {
        val suspiciousRecord = BASIC_TH_RECORD.copy(description = null)
        assertThat(BASIC_TH_PAGE.copy(transactionHistoryRecords = listOf(BASIC_TH_RECORD, suspiciousRecord)).isSuspicious()).isTrue()
    }
    @Test
    fun testToCsv() {
        assertThat(BASIC_TH_PAGE.toCsv(ACCOUNT_NUMBER)).isEqualTo("4/3/2020,\"test\",-500.00,,$ACCOUNT_NUMBER,\"AG-12345\",4/7/2020,2,\"$FILENAME\",1")
    }

    @Test
    fun testToCsvMultipleRecords() {
        val page = BASIC_TH_PAGE.copy(
            transactionHistoryRecords = listOf(BASIC_TH_RECORD, BASIC_TH_RECORD.copy(amount = 500.00))
        )
        assertThat(page.toCsv(ACCOUNT_NUMBER)).isEqualTo(
            """
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                4/3/2020,"test",500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
            """.trimIndent()
        )
    }

    @Test
    fun testToCsvMultipleRecordsSorted() {
        val page = BASIC_TH_PAGE.copy(
            transactionHistoryRecords = listOf(
                BASIC_TH_RECORD,
                BASIC_TH_RECORD.copy(amount = null),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("3/8", "2019")),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("8/3", "2024"))
            )
        )
        assertThat(page.toCsv(ACCOUNT_NUMBER)).isEqualTo(
            """
                3/8/2019,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                4/3/2020,"test",,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                8/3/2024,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
            """.trimIndent()
        )
    }
}