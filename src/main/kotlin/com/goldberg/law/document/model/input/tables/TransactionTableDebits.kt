package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableDebitsRecord.Companion.toTransactionTableDebitsRecord

data class TransactionTableDebits @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableDebitsRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableDebits(): TransactionTableDebits? =
            this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_DEBITS]?.valueList?.let { table ->
                TransactionTableDebits(records = table.map { it.toTransactionTableDebitsRecord() })
            }
    }
}