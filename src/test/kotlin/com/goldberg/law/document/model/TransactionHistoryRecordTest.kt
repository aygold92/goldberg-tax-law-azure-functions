package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_PDF_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.CHECK_FILE_PAGE
import com.goldberg.law.document.model.ModelValues.DEFAULT_BATES_STAMP_MAP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.TEST_TRANSACTION_ID
import com.goldberg.law.document.model.ModelValues.batesStampsMap
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord.SuspiciousReasons
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionHistoryRecordTest {
    @Test
    fun testNonSuspiciousRecords() {
        assertThat(BASIC_TH_RECORD.isSuspicious()).isFalse()
        assertThat(newHistoryRecord(amount = 500.00).isSuspicious()).isFalse()
    }

    @Test
    fun testSuspiciousRecordsNullFields() {
        assertThat(TransactionHistoryRecord(id = TEST_TRANSACTION_ID, filePageNumber = 2).isSuspicious()).isTrue()
        assertThat(newHistoryRecord(date = null).isSuspicious()).isTrue()
        assertThat(newHistoryRecord(description = null).isSuspicious()).isTrue()
        assertThat(newHistoryRecord(amount = 0.0).isSuspicious()).isTrue()
        assertThat(newHistoryRecord(amount = null).isSuspicious()).isTrue()
    }

    @Test
    fun testSuspiciousRecordsEmptyCheckNumber() {
        assertThat(newHistoryRecord(description = TransactionHistoryRecord.CHECK_DESCRIPTION).isSuspicious()).isTrue()
        assertThat(newHistoryRecord(description = TransactionHistoryRecord.CHECK_DESCRIPTION_ALT).isSuspicious()).isTrue()
    }

    @Test
    fun testSuspiciousRecordsReasonSingle() {
        val suspiciousRecord = newHistoryRecord(description = "Check")
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
        assertThat(BASIC_TH_RECORD.toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP))
            .isEqualTo("4/3/2020,\"test\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,,")
    }

    @Test
    fun testToCsvWithCheck() {
        assertThat(
            newHistoryRecord(description = TransactionHistoryRecord.CHECK_DESCRIPTION, checkNumber = 1234)
                .toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP)
        ).isEqualTo(
            "4/3/2020,\"Check 1234\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,,"
        )
    }

    @Test
    fun testToCsvWithCheckAndDescription() {
        assertThat(
            newHistoryRecord(checkNumber = 1234).toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP)
        ).isEqualTo(
            "4/3/2020,\"test Check 1234\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,,"
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
            newHistoryRecord(checkNumber = 1234, checkData = newCheckData(1000)).toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP)
        ).isEqualTo(
            "4/3/2020,\"test Check 1234: Some Guy - check desc\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,\"$CHECK_BATES_STAMP\",\"$CHECK_FILENAME\",[$CHECK_FILE_PAGE]"
        )
    }

    @Test
    fun testToCsvWithCheckAndCheckData() {
        assertThat(
            newHistoryRecord(checkNumber = 1234, description = "Check", checkData = newCheckData(1000)).toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP)
        ).isEqualTo(
            "4/3/2020,\"Check 1234: Some Guy - check desc\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,\"$CHECK_BATES_STAMP\",\"$CHECK_FILENAME\",[$CHECK_FILE_PAGE]"
        )
    }

    @Test
    fun testToCsvWithCheckNoDescriptionCheckData() {
        assertThat(
            newHistoryRecord(checkNumber = 1234, description = null, checkData = newCheckData(1000)).toCsv(FIXED_STATEMENT_DATE, ACCOUNT_NUMBER, BASIC_PDF_METADATA, DEFAULT_BATES_STAMP_MAP)
        ).isEqualTo(
            "4/3/2020,\"Check 1234: Some Guy - check desc\",-500.00,,\"WF Bank - 1234567890\",\"$BATES_STAMP\",4/7/2020,\"$FILENAME\",2,\"$CHECK_BATES_STAMP\",\"$CHECK_FILENAME\",[$CHECK_FILE_PAGE]"
        )
    }
}