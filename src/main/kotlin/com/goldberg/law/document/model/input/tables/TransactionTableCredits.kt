package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsRecord.Companion.toTransactionTableCreditsRecord

class TransactionTableCredits(records: List<TransactionTableCreditsRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableCredits(): TransactionTableCredits? =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_CREDITS]?.value) as? List<*>)?.let { table ->
                TransactionTableCredits(records = table.map { it!!.asDocumentField().toTransactionTableCreditsRecord() })
            }
    }
}