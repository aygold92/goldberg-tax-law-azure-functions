package com.goldberg.law.document.model.input

import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.FIXED_TRANSACTION_DATE
import com.goldberg.law.document.model.ModelValues.newPdfMetadata
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.input.StatementDataModel.Companion.NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION
import com.goldberg.law.document.model.input.tables.TransactionTableAmount
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.asCurrency
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class StatementDataModelTest {
    @Test
    fun testNFCUBank() {
        val records = listOf(
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION, null, 1),
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, "test", 50.asCurrency(), 1),
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, "test", 50.asCurrency(), 1),
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION, null, 1),
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, "test2", 50.asCurrency(), 1),
            TransactionTableAmountRecord(FIXED_TRANSACTION_DATE, "test2", 50.asCurrency(), 1),
        )
        val model = StatementModelValues.newStatementModel(pageMetadata = newPdfMetadata(classification = DocumentType.BankTypes.NFCU_BANK)).copy(accountNumber = null, summaryOfAccountsTable = SummaryOfAccountsTable(records = listOf(
            SummaryOfAccountsTableRecord(ACCOUNT_NUMBER, 100.asCurrency(), 200.asCurrency()),
            SummaryOfAccountsTableRecord(OTHER_ACCOUNT_NUMBER, 0.asCurrency(), 100.asCurrency()),
        )), transactionTableAmount = TransactionTableAmount(records))

        val result = model.toBankStatement()

        assertThat(result).hasSize(2)
        assertThat(result[0].transactions).isEqualTo(
            listOf(
                TransactionHistoryRecord(records[1].id, FIXED_TRANSACTION_DATE, null,"test", 50.asCurrency(), 2),
                TransactionHistoryRecord(records[2].id, FIXED_TRANSACTION_DATE, null,"test", 50.asCurrency(), 2),
            )
        )

        assertThat(result[0].date).isEqualTo(FIXED_STATEMENT_DATE)
        assertThat(result[0].accountNumber).isEqualTo(ACCOUNT_NUMBER)
        assertThat(result[0].beginningBalance?.asCurrency()).isEqualTo(100.asCurrency())
        assertThat(result[0].endingBalance?.asCurrency()).isEqualTo(200.asCurrency())

        assertThat(result[1].date).isEqualTo(FIXED_STATEMENT_DATE)
        assertThat(result[1].beginningBalance?.asCurrency()).isEqualTo(0.asCurrency())
        assertThat(result[1].endingBalance?.asCurrency()).isEqualTo(100.asCurrency())
        assertThat(result[1].accountNumber).isEqualTo(OTHER_ACCOUNT_NUMBER)

        assertThat(result[1].transactions).isEqualTo(
            listOf(
                TransactionHistoryRecord(records[4].id, FIXED_TRANSACTION_DATE, null,"test2", 50.asCurrency(), 2),
                TransactionHistoryRecord(records[5].id, FIXED_TRANSACTION_DATE, null,"test2", 50.asCurrency(), 2),
            )
        )
    }

    companion object {
        const val OTHER_ACCOUNT_NUMBER = "other"
    }
}