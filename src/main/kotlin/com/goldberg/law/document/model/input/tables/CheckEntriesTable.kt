package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow.Companion.toCheckEntriesTableRow

data class CheckEntriesTable(val images: List<CheckEntriesTableRow>) {
    companion object {
        fun AnalyzedDocument.getCheckImageTable() =
            this.fields[CheckDataModel.Keys.CHECK_ENTRIES_TABLE]?.valueList?.let { table ->
                CheckEntriesTable(images = table.map { it.toCheckEntriesTableRow() })
            }
    }
}