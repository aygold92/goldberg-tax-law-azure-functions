package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow.Companion.toCheckEntriesTableRow

data class CheckEntriesTable @JsonCreator constructor(@JsonProperty("images") val images: List<CheckEntriesTableRow>) {
    companion object {
        fun AnalyzedDocument.getCheckImageTable() =
            ((this.fields[CheckDataModel.Keys.CHECK_ENTRIES_TABLE]?.value) as? List<*>)?.let { table ->
                CheckEntriesTable(images = table.map { it!!.asDocumentField().toCheckEntriesTableRow() })
            }
    }
}