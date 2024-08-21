package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.AccountSummaryRecord.Companion.getAccountSummaryRecord
import com.goldberg.law.document.model.input.tables.asDocumentField

class SummaryOfAccounts(val records: List<AccountSummaryRecord>) {

    // note: assumes that records are in order. Pick the first record (starting from the end) where the record is
    //
    fun findRelevantAccount(pageNum: Int): AccountSummaryRecord = records.reversed().find {
        it.startPage != null && pageNum <= it.startPage
    } ?: records.first()
    companion object {
        fun AnalyzedDocument.getSummaryOfAccounts(): SummaryOfAccounts? =
            (this.fields[StatementDataModel.Keys.ACCOUNT_SUMMARY_TABLE]?.value as? List<*>)?.let { summary ->
                SummaryOfAccounts(
                    records = summary.map {
                        it!!.asDocumentField().getAccountSummaryRecord()
                    }
                )
            }
    }
}
