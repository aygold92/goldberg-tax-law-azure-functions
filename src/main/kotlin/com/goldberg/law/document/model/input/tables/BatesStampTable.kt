package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.BatesStampTableRow.Companion.toBatesStampTableRow

/**
 * The Bates Stamp Table will always map the file number in the document provided to the service (which is only part of the original document)
 * Therefore, the page numbers will always start [1,2 3...] regardless of what page in the original document
 */
data class BatesStampTable @JsonCreator constructor(@JsonProperty("batesStamps") val batesStamps: List<BatesStampTableRow>) {
    companion object {
        fun AnalyzedDocument.getBatesStampTable() =
            this.fields[StatementDataModel.Keys.BATES_STAMPS]?.valueList?.let { table ->
                BatesStampTable(batesStamps = table.map { it.toBatesStampTableRow() })
            }
    }
}