package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.roundToTwoDecimalPlaces

class TransactionTableDebitsRecord(
    val date: String?,
    val description: String?,
    val additions: Double?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDateStatementYearOverride(this.date, statementYear),
        description = this.description,
        amount = additions,
        pageMetadata = metadata
    )

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val SUBTRACTIONS = "Subtractions"
    }

    companion object {
        fun DocumentField.toTransactionTableDebitsRecord() = this.getFieldMapHack().let { recordFields ->
            TransactionTableAmountRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                description = recordFields[Keys.DESCRIPTION]?.valueAsString,
                amount = recordFields[Keys.SUBTRACTIONS]?.valueAsDouble?.roundToTwoDecimalPlaces(),
            )
        }
    }
}