package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawalRecord.Companion.toTransactionTableDepositWithdrawalRecord

class TransactionTableDepositWithdrawal(records: List<TransactionTableDepositWithdrawalRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableDepositWithdrawal() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_DEPOSIT_WITHDRAWAL]?.value) as? List<*>)?.let { table ->
                TransactionTableDepositWithdrawal(
                    records = table.map {
                        it!!.asDocumentField().toTransactionTableDepositWithdrawalRecord()
                    })
            }
    }
}