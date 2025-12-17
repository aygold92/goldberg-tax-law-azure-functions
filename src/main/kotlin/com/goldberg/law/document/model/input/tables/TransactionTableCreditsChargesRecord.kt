package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.pageNumber
import java.math.BigDecimal
import java.util.*

data class TransactionTableCreditsChargesRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("credits") val credits: BigDecimal?,
    @JsonProperty("charges") val charges: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionDetails(statementDate: Date?, classification: Classification): TransactionDetails = TransactionDetails(
        transactionId = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        // a credit represents a net increase in money, a charge is a decrease
        amount = this.credits ?: this.charges?.negate(),
        filePageNumber = classification.pagesOrdered[page - 1],
        checkNumber = null,
        checkId = null,
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val CREDITS = "Credits"
        const val CHARGES = "Charges"
    }

    companion object {
        fun DocumentField.toTransactionTableCreditsChargesRecord() = this.valueMap.let { recordFields ->
            TransactionTableCreditsChargesRecord(
                date = recordFields[Keys.DATE]?.valueString,
                description = recordFields[Keys.DESCRIPTION]?.valueString,
                // for some reason Wells Fargo can give negative values in the charges column for fees (why wouldn't they use credits??)
                credits = recordFields[Keys.CREDITS]?.currencyValue(),
                charges = recordFields[Keys.CHARGES]?.currencyValue(),
                page = recordFields.pageNumber()
            )
        }
    }
}