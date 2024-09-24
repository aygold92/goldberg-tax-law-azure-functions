package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableDebitsRecord.Companion.toTransactionTableDebitsRecord

data class TransactionTableDebits @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableAmountRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableDebits(): TransactionTableDebits? =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_DEBITS]?.value) as? List<*>)?.let { table ->
                TransactionTableDebits(records = table.map { it!!.asDocumentField().toTransactionTableDebitsRecord() })
            }
    }
}