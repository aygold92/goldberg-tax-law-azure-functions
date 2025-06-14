package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.FIXED_TRANSACTION_DATE
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord
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
        val statementRecord = TransactionTableAmountRecord("6/1", DESCRIPTION, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_NORMAL, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("6 1 2020"), id = statementRecord.id))
    }

    @Test
    fun testStatementDateEnd() {
        val statementRecord = TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_END, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2020"), id = statementRecord.id))
    }

    @Test
    fun testStatementDateBeginningAdjusted() {
        val statementRecord = TransactionTableAmountRecord("12/5", DESCRIPTION, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("12 5 2019"), id = statementRecord.id))
    }

    @Test
    fun testStatementDateBeginningNotAdjusted() {
        val statementRecord = TransactionTableAmountRecord("1/5", DESCRIPTION, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(date = normalizeDate("1 5 2020"), id = statementRecord.id))
    }

    @Test
    fun testAmountRecordIsPositiveForBank() {
        val statementRecord = TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = 500.0, id = statementRecord.id))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumber() {
        val checkDescription = "Check 4892"
        val statementRecord = TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, checkDescription, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(checkNumber = 4892, description = checkDescription, id = statementRecord.id))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumberLeadingZeros() {
        val checkDescription = "Check 004892"
        val statementRecord = TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, checkDescription, AMOUNT)
        assertThat(statementRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(checkNumber = 4892, description = checkDescription, id = statementRecord.id))
    }

    @Test
    fun testDebitsCreditsRecordsAreFlippedForCreditCard() {
        var debitsRecord = TransactionTableDebitsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
        assertThat(debitsRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = -500.0, id = debitsRecord.id))

        debitsRecord = TransactionTableDebitsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
        assertThat(debitsRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(amount = -500.0, id = debitsRecord.id))


        var creditsRecord = TransactionTableCreditsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
        assertThat(creditsRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.BANK))
            .isEqualTo(newHistoryRecord(amount = 500.0, id = creditsRecord.id))

        creditsRecord = TransactionTableCreditsRecord(FIXED_TRANSACTION_DATE, DESCRIPTION, AMOUNT)
        assertThat(creditsRecord.toTransactionHistoryRecord(STATEMENT_DATE_BEGINNING, BASIC_TH_PAGE_METADATA, DocumentType.CREDIT_CARD))
            .isEqualTo(newHistoryRecord(amount = 500.0, id = creditsRecord.id))
    }


    companion object {
        val STATEMENT_DATE_NORMAL = fromWrittenDate("6 5 2020")
        val STATEMENT_DATE_BEGINNING = fromWrittenDate("1 15 2020")
        val STATEMENT_DATE_END = fromWrittenDate("12 15 2020")
        const val DESCRIPTION = "test"
        val AMOUNT = 500.0.asCurrency()
    }
}