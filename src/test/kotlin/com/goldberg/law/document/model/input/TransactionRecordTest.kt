package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsRecord
import com.goldberg.law.document.model.input.tables.TransactionTableDebitsRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.DEFAULT_AMOUNT
import com.goldberg.law.entity.EntityValues.DEFAULT_DATE
import com.goldberg.law.entity.EntityValues.DEFAULT_DESCRIPTION
import com.goldberg.law.entity.EntityValues.DEFAULT_STATEMENT_DATE
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newTransactionDetails
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class TransactionRecordTest {
    @Test
    fun testStatementDateNormal() {
        val statementRecord = TransactionTableAmountRecord("6/1", DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(STATEMENT_DATE_NORMAL, newClassification()))
            .isEqualTo(newTransactionDetails(date = normalizeDate("6 1 2020"), transactionId =  statementRecord.id))
    }

    @Test
    fun testStatementDateEnd() {
        val statementRecord = TransactionTableAmountRecord("12/5", DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(STATEMENT_DATE_END, newClassification()))
            .isEqualTo(newTransactionDetails(date = normalizeDate("12 5 2020"), transactionId =  statementRecord.id))
    }

    @Test
    fun testStatementDateBeginningOfYearAdjusted() {
        val statementRecord = TransactionTableAmountRecord("12/5", DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(fromWrittenDate("1 15 2020"), newClassification()))
            .isEqualTo(newTransactionDetails(date = normalizeDate("12 5 2019"), transactionId =  statementRecord.id))
    }

    @Test
    fun testStatementDateBeginningOfYearNotAdjusted() {
        val statementRecord = TransactionTableAmountRecord("1/5", DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(STATEMENT_DATE_BEGINNING, newClassification()))
            .isEqualTo(newTransactionDetails(date = normalizeDate("1 5 2020"), transactionId =  statementRecord.id))
    }

    @Test
    fun testAmountRecordIsPositiveForBank() {
        val statementRecord = TransactionTableAmountRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(transactionId =  statementRecord.id))
    }

    @Test
    fun testAmountRecordIsNegativeForCreditCard() {
        val statementRecord = TransactionTableAmountRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification(type = DocumentType.CreditCardTypes.C1_CC)))
            .isEqualTo(newTransactionDetails(amount = -DEFAULT_AMOUNT, transactionId =  statementRecord.id))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumber() {
        val checkDescription = "Check 4892"
        val statementRecord = TransactionTableAmountRecord(DEFAULT_DATE, checkDescription, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(checkNumber = 4892, description = checkDescription, transactionId =  statementRecord.id))
    }

    @Test
    fun testCheckDescriptionBecomesCheckNumberLeadingZeros() {
        val checkDescription = "Check 004892"
        val statementRecord = TransactionTableAmountRecord(DEFAULT_DATE, checkDescription, DEFAULT_AMOUNT, 1)
        assertThat(statementRecord.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(checkNumber = 4892, description = checkDescription, transactionId =  statementRecord.id))
    }

    @Test
    fun testDebitsRecordsAlwaysNegative() {
        val amount = (500).asCurrency()
        val debitsRecordCCPositive = TransactionTableDebitsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, amount, 1)
        assertThat(debitsRecordCCPositive.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification(type = DocumentType.CreditCardTypes.C1_CC)))
            .isEqualTo(newTransactionDetails(amount = -amount, transactionId =  debitsRecordCCPositive.id))

        val debitsRecordCCNegative = TransactionTableDebitsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, -amount, 1)
        assertThat(debitsRecordCCNegative.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification(type = DocumentType.CreditCardTypes.C1_CC)))
            .isEqualTo(newTransactionDetails(amount = -amount, transactionId =  debitsRecordCCNegative.id))

        val debitsRecordBankPositive = TransactionTableDebitsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, amount, 1)
        assertThat(debitsRecordBankPositive.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(amount = -amount, transactionId =  debitsRecordBankPositive.id))

        val debitsRecordBankNegative = TransactionTableDebitsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, -amount, 1)
        assertThat(debitsRecordBankNegative.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(amount = -amount, transactionId =  debitsRecordBankNegative.id))
    }

    @Test
    fun testCreditsRecordsAlwaysPositive() {
        val amount = (500).asCurrency()
        val creditsRecordCCPositive = TransactionTableCreditsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, amount, 1)
        assertThat(creditsRecordCCPositive.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification(type = DocumentType.CreditCardTypes.C1_CC)))
            .isEqualTo(newTransactionDetails(amount = amount, transactionId =  creditsRecordCCPositive.id))

        val creditsRecordCCNegative = TransactionTableCreditsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, -amount, 1)
        assertThat(creditsRecordCCNegative.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification(type = DocumentType.CreditCardTypes.C1_CC)))
            .isEqualTo(newTransactionDetails(amount = amount, transactionId =  creditsRecordCCNegative.id))

        val creditsRecordBankPositive = TransactionTableCreditsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, amount, 1)
        assertThat(creditsRecordBankPositive.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(amount = amount, transactionId =  creditsRecordBankPositive.id))

        val creditsRecordBankNegative = TransactionTableCreditsRecord(DEFAULT_DATE, DEFAULT_DESCRIPTION, -amount, 1)
        assertThat(creditsRecordBankNegative.toTransactionDetails(DEFAULT_STATEMENT_DATE, newClassification()))
            .isEqualTo(newTransactionDetails(amount = amount, transactionId =  creditsRecordBankNegative.id))
    }


    companion object {
        val STATEMENT_DATE_NORMAL = fromWrittenDate("6 5 2020")
        val STATEMENT_DATE_BEGINNING = fromWrittenDate("1 15 2020")
        val STATEMENT_DATE_END = fromWrittenDate("12 15 2020")
    }
}