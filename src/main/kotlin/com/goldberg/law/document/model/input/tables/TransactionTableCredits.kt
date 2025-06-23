package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsRecord.Companion.toTransactionTableCreditsRecord

data class TransactionTableCredits @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableCreditsRecord>): TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableCredits(): TransactionTableCredits? =
            this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_CREDITS]?.valueList?.let { table ->
                TransactionTableCredits(records = table.map { it.toTransactionTableCreditsRecord() })
            }
    }
}