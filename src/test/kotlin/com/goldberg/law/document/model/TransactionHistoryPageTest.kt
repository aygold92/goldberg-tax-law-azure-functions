package com.goldberg.law.document.model

import com.goldberg.law.util.fromTransactionDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryPageTest {
    @Test
    fun testNonSuspiciousPage() {
        assertThat(BASIC_TH_PAGE.isSuspicious).isFalse()
    }

    @Test
    fun testSuspiciousPageNullFields() {
        assertThat(TransactionHistoryRecord().isSuspicious).isTrue()
        assertThat(BASIC_TH_PAGE.copy(statementDate = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_PAGE.copy(statementPageNum = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_PAGE.copy(statementTotalPages = null).isSuspicious).isTrue()
    }

    @Test
    fun testSuspiciousPageRecord() {
        val suspiciousRecord = BASIC_TH_RECORD.copy(description = null)
        assertThat(BASIC_TH_PAGE.copy(transactionHistoryRecords = listOf(BASIC_TH_RECORD, suspiciousRecord)).isSuspicious).isTrue()
    }
    @Test
    fun testToCsv() {
        assertThat(BASIC_TH_PAGE.toCsv()).isEqualTo("4/3/2020,,\"test\",,500.00,\"$FILENAME\",1,4/7/2020,2,5")
    }

    @Test
    fun testToCsvMultipleRecords() {
        val page = BASIC_TH_PAGE.copy(
            transactionHistoryRecords = listOf(BASIC_TH_RECORD, BASIC_TH_RECORD.copy(depositAmount = 500.00))
        )
        assertThat(page.toCsv()).isEqualTo(
            """
                4/3/2020,,"test",,500.00,"$FILENAME",1,4/7/2020,2,5
                4/3/2020,,"test",500.00,500.00,"$FILENAME",1,4/7/2020,2,5
            """.trimIndent()
        )
    }

    companion object {
        const val FILENAME = "test.pdf"

        private val FIXED_TRANSACTION_DATE = fromTransactionDate("4/3", "2020")
        private val FIXED_STATEMENT_DATE = fromTransactionDate("4/7", "2020")

        private val BASIC_TH_RECORD = TransactionHistoryRecord(
            date = FIXED_TRANSACTION_DATE,
            description = "test",
            withdrawalAmount = 500.00
        )

        private val BASIC_TH_PAGE = TransactionHistoryPage(
            filename = FILENAME,
            filePageNumber = 1,
            statementDate = FIXED_STATEMENT_DATE,
            statementPageNum = 2,
            statementTotalPages = 5,
            transactionHistoryRecords = listOf(BASIC_TH_RECORD)
        )
    }
}