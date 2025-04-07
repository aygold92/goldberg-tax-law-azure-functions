package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.FIXED_TRANSACTION_DATE
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord
import com.goldberg.law.document.model.input.tables.TransactionTableCredits
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsRecord
import com.goldberg.law.document.model.input.tables.TransactionTableDebitsRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionRecordTest {
    @Test
    fun testStatementDateNormal() {
        assertThat(TransactionTableAmountRecord("6/1", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_NORMAL, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("6 1 2020")))
    }

    @Test
    fun testStatementDateEnd() {
        assertThat(TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_END, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2020")))
    }

    @Test
    fun testStatementDateBeginningAdjusted() {
        assertThat(TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2019")))
    }

    @Test
    fun testStatementDateBeginningNotAdjusted() {
        assertThat(TransactionTableAmountRecord("1/5", DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("1 5 2020")))
    }

    @Test
    fun testAmountRecordIsPositiveForBank() {
        assertThat(TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = 500.0))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumber() {
        val checkDescription = "Check 4892"
        assertThat(TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, checkDescription, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(checkNumber = 4892, description = checkDescription))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumberLeadingZeros() {
        val checkDescription = "Check 004892"
        assertThat(TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, checkDescription, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(checkNumber = 4892, description = checkDescription))
    }

    @Test
    fun testDebitsCreditsRecordsAreFlippedForCreditCard() {
        assertThat(TransactionTableDebitsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = -500.0))

        assertThat(TransactionTableDebitsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(amount = 500.0))

        assertThat(
            TransactionTableCreditsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = 500.0))

        assertThat(TransactionTableCreditsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
            .toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(amount = -500.0))
    }


    companion object {
        val STATEMENT_DATE_NORMAL = fromWrittenDate("6 5 2020")
        val STATEMENT_DATE_BEGINNING = fromWrittenDate("1 15 2020")
        val STATEMENT_DATE_END = fromWrittenDate("12 15 2020")
        const val DESCRIPTION = "test"
        val AMOUNT = 500.0.asCurrency()
    }
}