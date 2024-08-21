package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableChecksRecord.Companion.toTransactionTableChecksRecord

class TransactionTableChecks(records: List<TransactionTableChecksRecord>) : TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableChecks() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_CHECKS]?.value) as? List<*>)?.let { table ->
                TransactionTableChecks(records = table.map { it!!.asDocumentField().toTransactionTableChecksRecord() })
            }
    }
}