package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsChargesRecord.Companion.toTransactionTableCreditsChargesRecord

data class TransactionTableCreditsCharges @JsonCreator constructor(@JsonProperty("records") override val records: List<TransactionTableCreditsChargesRecord>) : TransactionTable(records) {
    companion object {
        fun AnalyzedDocument.getTransactionTableCreditsCharges() =
            ((this.fields[StatementDataModel.Keys.TRANSACTION_TABLE_CREDITS_CHARGES]?.value) as? List<*>)?.let { table ->
                TransactionTableCreditsCharges(records = table.map {
                    it!!.asDocumentField().toTransactionTableCreditsChargesRecord()
                })
            }
    }
}