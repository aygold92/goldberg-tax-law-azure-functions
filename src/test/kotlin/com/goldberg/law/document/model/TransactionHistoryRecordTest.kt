package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.FIXED_TRANSACTION_DATE
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord.SuspiciousReasons
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryRecordTest {
    @Test
    fun testNonSuspiciousRecords() {
        assertThat(BASIC_TH_RECORD.isSuspicious).isFalse()
        assertThat(BASIC_TH_RECORD.copy(amount = 500.00).isSuspicious).isFalse()
    }

    @Test
    fun testSuspiciousRecordsNullFields() {
        assertThat(TransactionHistoryRecord().isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(date = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = null).isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(amount = 0.0).isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(amount = null).isSuspicious).isTrue()
    }

    @Test
    fun testSuspiciousRecordsEmptyCheckNumber() {
        assertThat(BASIC_TH_RECORD.copy(description = "Check").isSuspicious).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = "Check", checkNumber = 1000).isSuspicious).isFalse()
    }

    @Test
    fun testSuspiciousRecordsReasonSingle() {
        val suspiciousRecord = BASIC_TH_RECORD.copy(description = "Check")
        assertThat(suspiciousRecord.isSuspicious).isTrue()
        assertThat(suspiciousRecord.isSuspiciousReasons.size).isEqualTo(1)
        assertThat(suspiciousRecord.isSuspiciousReasons[0]).contains("no check number")
    }

    @Test
    fun testSuspiciousRecordsReasonMultiple() {
        val suspiciousRecord = TransactionHistoryRecord(
            description = "check",
            amount = null
        )
        assertThat(suspiciousRecord.isSuspicious).isTrue()
        assertThat(suspiciousRecord.isSuspiciousReasons.size).isEqualTo(3)
        assertThat(suspiciousRecord.isSuspiciousReasons).anyMatch { it == SuspiciousReasons.CHECK_WITHOUT_NUMBER.format("null") }
        assertThat(suspiciousRecord.isSuspiciousReasons).anyMatch { it == SuspiciousReasons.ONE_OF_WITHDRAWAL_DEPOSIT.format("null") }
        assertThat(suspiciousRecord.isSuspiciousReasons).anyMatch { it == SuspiciousReasons.NO_DATE }
    }

    @Test
    fun testToCsvBasicRecord() {
        assertThat(BASIC_TH_RECORD.toCsv()).isEqualTo("4/3/2020,\"test\",-500.00")
    }

    @Test
    fun testToCsvFullRecord() {
        assertThat(
            TransactionHistoryRecord(
            date = FIXED_TRANSACTION_DATE,
            checkNumber = 1234,
            description = "check",
            amount = 500.00,
        ).toCsv()).isEqualTo("4/3/2020,\"Check 1234\",500.00")
    }
}