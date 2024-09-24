package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.currencyValue
import java.math.BigDecimal
import java.util.*

data class TransactionTableCreditsChargesRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("credits") val credits: BigDecimal?,
    @JsonProperty("charges") val charges: BigDecimal?
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = this.description,
        // a credit represents a net increase in money, a charge is a decrease
        amount = this.credits ?: this.charges?.negate(),
        pageMetadata = metadata
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val CREDITS = "Credits"
        const val CHARGES = "Charges"
    }

    companion object {
        fun DocumentField.toTransactionTableCreditsChargesRecord() = this.getFieldMapHack().let { recordFields ->
            TransactionTableCreditsChargesRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                description = recordFields[Keys.DESCRIPTION]?.valueAsString,
                // for some reason Wells Fargo can give negative values in the charges column for fees (why wouldn't they use credits??)
                credits = recordFields[Keys.CREDITS]?.currencyValue(),
                charges = recordFields[Keys.CHARGES]?.currencyValue(),
            )
        }
    }
}