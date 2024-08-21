package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableDebitsRecord.Companion.toTransactionTableDebitsRecord

class TransactionTableDebits(records: List<TransactionTableAmountRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableDebits(): TransactionTableDebits? =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_DEBITS]?.value) as? List<*>)?.let { table ->
                TransactionTableDebits(records = table.map { it!!.asDocumentField().toTransactionTableDebitsRecord() })
            }
    }
}