package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.CHECK_FILE_PAGE
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord.SuspiciousReasons
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryRecordTest {
    @Test
    fun testNonSuspiciousRecords() {
        assertThat(BASIC_TH_RECORD.isSuspicious()).isFalse()
        assertThat(BASIC_TH_RECORD.copy(amount = 500.00).isSuspicious()).isFalse()
    }

    @Test
    fun testSuspiciousRecordsNullFields() {
        assertThat(TransactionHistoryRecord(pageMetadata = BASIC_TH_PAGE_METADATA).isSuspicious()).isTrue()
        assertThat(BASIC_TH_RECORD.copy(date = null).isSuspicious()).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = null).isSuspicious()).isTrue()
        assertThat(BASIC_TH_RECORD.copy(amount = 0.0).isSuspicious()).isTrue()
        assertThat(BASIC_TH_RECORD.copy(amount = null).isSuspicious()).isTrue()
    }

    @Test
    fun testSuspiciousRecordsEmptyCheckNumber() {
        assertThat(BASIC_TH_RECORD.copy(description = "Check").isSuspicious()).isTrue()
        assertThat(BASIC_TH_RECORD.copy(description = "Check", checkNumber = 1000).isSuspicious()).isFalse()
    }

    @Test
    fun testSuspiciousRecordsReasonSingle() {
        val suspiciousRecord = BASIC_TH_RECORD.copy(description = "Check")
        assertThat(suspiciousRecord.isSuspicious()).isTrue()
        assertThat(suspiciousRecord.getSuspiciousReasons().size).isEqualTo(1)
        assertThat(suspiciousRecord.getSuspiciousReasons()[0]).contains("no check number")
    }

    @Test
    fun testSuspiciousRecordsReasonMultiple() {
        val suspiciousRecord = newHistoryRecord(
            description = "check",
            amount = null,
            date = null,
        )
        assertThat(suspiciousRecord.isSuspicious()).isTrue()
        assertThat(suspiciousRecord.getSuspiciousReasons().size).isEqualTo(3)
        assertThat(suspiciousRecord.getSuspiciousReasons()).anyMatch { it == SuspiciousReasons.CHECK_WITHOUT_NUMBER.format("null") }
        assertThat(suspiciousRecord.getSuspiciousReasons()).anyMatch { it == SuspiciousReasons.NO_AMOUNT.format("null") }
        assertThat(suspiciousRecord.getSuspiciousReasons()).anyMatch { it == SuspiciousReasons.NO_DATE }
    }

    @Test
    fun testToCsvBasicRecord() {
        assertThat(BASIC_TH_RECORD.toCsv(ACCOUNT_NUMBER, BankTypes.WF_BANK))
            .isEqualTo("4/3/2020,\"test\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,2,\"$FILENAME\",1,")
    }

    @Test
    fun testToCsvWithCheck() {
        assertThat(
            BASIC_TH_RECORD.copy(description = TransactionHistoryRecord.CHECK_DESCRIPTION, checkNumber = 1234)
                .toCsv(ACCOUNT_NUMBER, BankTypes.WF_BANK)
        ).isEqualTo(
            "4/3/2020,\"Check 1234\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,2,\"$FILENAME\",1,"
        )
    }

    @Test
    fun testToCsvWithCheckAndDescription() {
        assertThat(
            BASIC_TH_RECORD.copy(checkNumber = 1234).toCsv(ACCOUNT_NUMBER, BankTypes.WF_BANK)
        ).isEqualTo(
            "4/3/2020,\"test Check 1234\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,2,\"$FILENAME\",1,"
        )
    }

    @Test
    fun testWithCheckData() {
        assertThat(newHistoryRecord(checkNumber = 1000).withCheckInfo(newCheckData(1000))).isEqualTo(
            newHistoryRecord(checkNumber = 1000, checkData = newCheckData(1000))
        )
    }

    @Test
    fun testToCsvWithCheckAndDescriptionAndCheckData() {
        assertThat(
            newHistoryRecord(checkNumber = 1234, checkData = newCheckData(1000)).toCsv(ACCOUNT_NUMBER, BankTypes.WF_BANK)
        ).isEqualTo(
            "4/3/2020,\"test Check 1234\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,2,\"$FILENAME\",1,\"$CHECK_BATES_STAMP\",\"$CHECK_FILENAME\",$CHECK_FILE_PAGE"
        )
    }
}