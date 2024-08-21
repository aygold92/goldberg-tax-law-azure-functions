package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.roundToTwoDecimalPlaces

class TransactionTableCreditsRecord(
    val date: String?,
    val description: String?,
    val additions: Double?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDate(this.date, statementYear),
        description = this.description,
        amount = additions
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
                additions = recordFields[Keys.ADDITIONS]?.valueAsDouble?.roundToTwoDecimalPlaces(),
            )
        }
    }
}