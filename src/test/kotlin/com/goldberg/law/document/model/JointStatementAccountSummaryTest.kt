package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE_DATE
import com.goldberg.law.document.model.input.SummaryOfAccountsTable
import com.goldberg.law.document.model.input.SummaryOfAccountsTableRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class JointStatementAccountSummaryTest {

    @Test
    fun testFindRelevantAccountMatches() {
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, 2))
            .isEqualTo(ACCOUNT_NUMBER)
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, 3))
            .isEqualTo(ACCOUNT_NUMBER)
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, 5))
            .isEqualTo(OTHER_ACCOUNT_NUMBER)
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, 7))
            .isEqualTo(OTHER_ACCOUNT_NUMBER)
    }

    @Test
    fun testFindRelevantAccountDoesNotMatch() {
        assertThat(SUMMARY.findRelevantAccountIfMatches(null, DocumentType.BankTypes.WF_BANK, 1)).isEqualTo(null)
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.EAGLE_BANK, 1)).isEqualTo(null)
        assertThat(SUMMARY.findRelevantAccountIfMatches(FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, null)).isEqualTo(null)
    }

    companion object {
        val OTHER_ACCOUNT_NUMBER = "78901"

        val SUMMARY = JointStatementAccountSummary(
            FIXED_STATEMENT_DATE_DATE, DocumentType.BankTypes.WF_BANK, SummaryOfAccountsTable(records = listOf(
            SummaryOfAccountsTableRecord(ACCOUNT_NUMBER, 3, 1000.asCurrency(), ZERO),
            SummaryOfAccountsTableRecord(OTHER_ACCOUNT_NUMBER, 5, 1000.asCurrency(), ZERO)
        )))
    }
}