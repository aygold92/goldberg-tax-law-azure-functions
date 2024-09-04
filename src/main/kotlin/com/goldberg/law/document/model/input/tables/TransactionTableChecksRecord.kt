package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.roundToTwoDecimalPlaces
import com.goldberg.law.util.valueAsInt

class TransactionTableChecksRecord(
    val date: String?,
    val number: Int?,
    val amount: Double?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?, metadata: TransactionHistoryPageMetadata): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDateStatementYearOverride(this.date, statementYear),
        description = TransactionHistoryRecord.CHECK_DESCRIPTION,
        checkNumber = this.number,
        amount = if (amount != null) 0.0 - amount else null,  // a check represents money leaving, so we subtract
        pageMetadata = metadata
    )

    object Keys {
        const val DATE = "Date"
        const val NUMBER = "Number"
        const val AMOUNT = "Amount"
    }

    companion object {
        fun DocumentField.toTransactionTableChecksRecord() = this.getFieldMapHack().let { recordFields ->
            TransactionTableChecksRecord(
                date = recordFields[Keys.DATE]?.valueAsString,
                number = recordFields[Keys.NUMBER]?.valueAsInt(),
                amount = recordFields[Keys.AMOUNT]?.valueAsDouble?.roundToTwoDecimalPlaces(),
            )
        }
    }
}