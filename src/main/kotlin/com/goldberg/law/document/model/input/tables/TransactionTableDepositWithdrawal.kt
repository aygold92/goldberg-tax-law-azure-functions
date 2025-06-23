package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawalRecord.Companion.toTransactionTableDepositWithdrawalRecord

data class TransactionTableDepositWithdrawal @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableDepositWithdrawalRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableDepositWithdrawal() =
            this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_DEPOSIT_WITHDRAWAL]?.valueList?.let { table ->
                TransactionTableDepositWithdrawal(
                    records = table.map {
                        it.toTransactionTableDepositWithdrawalRecord()
                    })
            }
    }
}