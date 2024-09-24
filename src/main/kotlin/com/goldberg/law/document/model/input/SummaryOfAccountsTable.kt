package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.SummaryOfAccountsTableRecord.Companion.getAccountSummaryRecord
import com.goldberg.law.document.model.input.tables.asDocumentField

data class SummaryOfAccountsTable @JsonCreator constructor(@JsonProperty("records") val records: List<SummaryOfAccountsTableRecord>) {

    // note: assumes that records are in order. Pick the first record (starting from the end) where the record is
    //
    fun findRelevantAccount(pageNum: Int): SummaryOfAccountsTableRecord = records.reversed().find {
        it.startPage != null && pageNum >= it.startPage
    } ?: records.first()
    companion object {
        fun AnalyzedDocument.getSummaryOfAccounts(): SummaryOfAccountsTable? =
            (this.fields[StatementDataModel.Keys.ACCOUNT_SUMMARY_TABLE]?.value as? List<*>)?.let { summary ->
                SummaryOfAccountsTable(
                    records = summary.map {
                        it!!.asDocumentField().getAccountSummaryRecord()
                    }
                )
            }
    }
}
