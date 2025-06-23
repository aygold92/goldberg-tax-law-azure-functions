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

data class TransactionTableDebitsRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("subtractions") val subtractions: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: ClassifiedPdfMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        id = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        amount = subtractions?.abs()?.negate(),
        filePageNumber = metadata.pagesOrdered[page - 1]
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val SUBTRACTIONS = "Subtractions"
    }

    companion object {
        fun DocumentField.toTransactionTableDebitsRecord() = this.valueMap.let { recordFields ->
            TransactionTableDebitsRecord(
                date = recordFields[Keys.DATE]?.valueString,
                description = recordFields[Keys.DESCRIPTION]?.valueString,
                subtractions = recordFields[Keys.SUBTRACTIONS]?.positiveCurrencyValue(),
                page = recordFields.pageNumber()
            )
        }
    }
}