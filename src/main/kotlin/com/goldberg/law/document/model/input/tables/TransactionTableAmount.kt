package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableAmountRecord.Companion.toTransactionTableAmountRecord

data class TransactionTableAmount @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableAmountRecord>) : TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableAmount() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_AMOUNT]?.value) as? List<*>)?.let { table ->
                TransactionTableAmount(records = table.map { it!!.asDocumentField().toTransactionTableAmountRecord() })
            }
    }
}