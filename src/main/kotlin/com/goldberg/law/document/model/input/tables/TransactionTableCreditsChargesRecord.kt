package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.roundToTwoDecimalPlaces
import kotlin.math.absoluteValue

class TransactionTableCreditsChargesRecord(
    val date: String?,
    val description: String?,
    val credits: Double?,
    val charges: Double?
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDate(this.date, statementYear),
        description = this.description,
        // a credit represents a net increase in money, a charge is a decrease
        amount = this.credits ?: this.charges?.let { 0 - it }
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
                // some handwritten values accidentally have negative values, but that's already built in here
                credits = recordFields[Keys.CREDITS]?.valueAsDouble?.roundToTwoDecimalPlaces()?.absoluteValue,
                charges = recordFields[Keys.CHARGES]?.valueAsDouble?.roundToTwoDecimalPlaces()?.absoluteValue,
            )
        }
    }
}