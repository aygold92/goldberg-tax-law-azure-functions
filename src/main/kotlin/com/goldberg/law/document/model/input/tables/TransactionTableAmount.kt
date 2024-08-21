package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord.Companion.toTransactionTableAmountRecord

class TransactionTableAmount(records: List<TransactionTableAmountRecord>) : TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableAmount() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_AMOUNT]?.value) as? List<*>)?.let { table ->
                TransactionTableAmount(records = table.map { it!!.asDocumentField().toTransactionTableAmountRecord() })
            }
    }
}