package com.goldberg.law.document.model

import com.goldberg.law.util.fromTransactionDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class TransactionHistoryRecordTest {
    @Test
    fun testNonSuspiciousRecords() {
        assertThat(BASIC_TH_RECORD.isSuspicious).isFalse()
        assertThat(BASIC_TH_RECORD.copy(withdrawalAmount = null, depositAmount = 500.00).isSuspicious).isFalse()
    }

    @Test
    fun testSuspiciousRecordsNullFields() {
        assertThat(TransactionHistoryRecord().isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(date = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(withdrawalAmount = null).isSuspicious).isTrue()
    }

    @Test
    fun testSuspiciousRecordsWithdrawalAndDeposit() {
        assertThat(BASIC_TH_RECORD.copy(depositAmount = 500.00).isSuspicious).isTrue()
    }

    @Test
    fun testSuspiciousRecordsEmptyCheckNumber() {
        assertThat(BASIC_TH_RECORD.copy(description = "Check").isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = "Check", checkNumber = 1000).isSuspicious).isFalse()
    }

    @Test
    fun testToCsvBasicRecord() {
        assertThat(BASIC_TH_RECORD.toCsv()).isEqualTo("4/7/2027,1234,check,500.00,500.00")
    }

    @Test
    fun testToCsvFullRecord() {
        assertThat(TransactionHistoryRecord(
            date = FIXED_DATE,
            checkNumber = 1234,
            description = "check",
            depositAmount = 500.00,
            withdrawalAmount = 500.00
        ).toCsv()).isEqualTo("4/7/2027,,check,,500.00")
    }

    companion object {
        private val FIXED_DATE = fromTransactionDate("4/7", "2020")
        private val BASIC_TH_RECORD = TransactionHistoryRecord(
            date = FIXED_DATE,
            description = "test",
            withdrawalAmount = 500.00
        )
    }
}