package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.getMonthInt
import com.goldberg.law.util.getYearSafe
import com.goldberg.law.util.roundToTwoDecimalPlaces

class TransactionTableAmountRecord(
    val date: String?,
    val description: String?,
    val amount: Double?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord {
        return TransactionHistoryRecord(
            date = fromWrittenDateStatementYearOverride(this.date, statementYear),
            description = this.description,
            // a positive entry on the credit card statement is a charge, which means money is going down
            // a negative entry is a credit, which means money goes up
            amount = if (this.amount == null) null else 0 - amount, // TODO: base this off a variable maybe?
            pageMetadata = metadata
        ).also {
            if (it.date?.getYearSafe() == "2021" && it.date.getMonthInt() == 11) {
                logger.error { "What??" }
            }
        }
    }

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val AMOUNT = "Amount"
    }

    companion object {
        fun DocumentField.toTransactionTableAmountRecord() = this.getFieldMapHack().let { recordFields ->
            TransactionTableAmountRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                description = recordFields[Keys.DESCRIPTION]?.valueAsString,
                amount = recordFields[Keys.AMOUNT]?.valueAsDouble?.roundToTwoDecimalPlaces()
            )
        }
    }
}