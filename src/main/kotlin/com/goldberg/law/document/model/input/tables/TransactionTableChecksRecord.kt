package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.hackToNumber
import com.goldberg.law.util.parseCurrency

class TransactionTableChecksRecord(
    val date: String?,
    val number: Int?,
    val amount: Double?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementYear: String?): TransactionHistoryRecord = TransactionHistoryRecord(
        date = fromWrittenDate(this.date, statementYear),
        description = TransactionHistoryRecord.CHECK_DESCRIPTION,
        checkNumber = this.number,
        amount = if (amount != null) 0.0 - amount else null  // a check represents money leaving, so we subtract
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
                number = recordFields[Keys.NUMBER]?.valueAsString?.hackToNumber()?.toInt(), // TODO: fix this in model
                amount = recordFields[Keys.AMOUNT]?.content?.parseCurrency() // TODO: fix this in model .valueAsDouble?.roundToTwoDecimalPlaces(),
            )
        }
    }
}