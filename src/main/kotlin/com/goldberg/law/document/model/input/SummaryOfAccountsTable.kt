package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.SummaryOfAccountsTableRecord.Companion.getAccountSummaryRecord

data class SummaryOfAccountsTable @JsonCreator constructor(@JsonProperty("records") val records: List<SummaryOfAccountsTableRecord>) {
    companion object {
        fun AnalyzedDocument.getSummaryOfAccounts(): SummaryOfAccountsTable? =
            this.fields[StatementDataModel.Keys.ACCOUNT_SUMMARY_TABLE]?.valueList?.let { summary ->
                SummaryOfAccountsTable(
                    records = summary.map { it.getAccountSummaryRecord() }
                )
            }
    }
}
