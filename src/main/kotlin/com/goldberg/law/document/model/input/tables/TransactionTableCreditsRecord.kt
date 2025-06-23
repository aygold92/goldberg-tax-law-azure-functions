package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.util.pageNumber
import com.goldberg.law.util.positiveCurrencyValue
import java.math.BigDecimal
import java.util.*

data class TransactionTableCreditsRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("additions") val additions: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: ClassifiedPdfMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        id = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        amount = additions,
        filePageNumber = metadata.pagesOrdered[page - 1]
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val ADDITIONS = "Additions"
    }

    companion object {
        fun DocumentField.toTransactionTableCreditsRecord() = this.valueMap.let { recordFields ->
            TransactionTableCreditsRecord(
                date = recordFields[Keys.DATE]?.valueString,
                description = recordFields[Keys.DESCRIPTION]?.valueString,
                additions = recordFields[Keys.ADDITIONS]?.positiveCurrencyValue(),
                page = recordFields.pageNumber()
            )
        }
    }
}