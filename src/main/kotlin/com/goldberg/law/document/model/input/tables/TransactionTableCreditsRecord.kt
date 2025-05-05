package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.positiveCurrencyValue
import java.math.BigDecimal
import java.util.*

data class TransactionTableCreditsRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("additions") val additions: BigDecimal?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): TransactionHistoryRecord = TransactionHistoryRecord(
        id = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        amount = additions,
        pageMetadata = metadata
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val ADDITIONS = "Additions"
    }

    companion object {
        fun DocumentField.toTransactionTableCreditsRecord() = this.getFieldMapHack().let { recordFields ->
            TransactionTableCreditsRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                description = recordFields[Keys.DESCRIPTION]?.valueAsString,
                additions = recordFields[Keys.ADDITIONS]?.positiveCurrencyValue(),
            )
        }
    }
}