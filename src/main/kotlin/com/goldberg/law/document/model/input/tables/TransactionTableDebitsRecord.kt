package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.TransactionDetails
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
    override fun toTransactionDetails(statementDate: Date?, classification: Classification): TransactionDetails = TransactionDetails(
        transactionId = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        amount = subtractions?.abs()?.negate(),
        filePageNumber = classification.pagesOrdered[page - 1],
        checkNumber = null,
        checkId = null,
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