package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableChecksRecord.Companion.toTransactionTableChecksRecord

data class TransactionTableChecks @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableChecksRecord>) : TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableChecks() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_CHECKS]?.value) as? List<*>)?.let { table ->
                TransactionTableChecks(records = table.map { it!!.asDocumentField().toTransactionTableChecksRecord() })
            }
    }
}