package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.valueAsInt
import java.math.BigDecimal
import java.util.*

data class TransactionTableChecksRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("number") val number: Int?,
    @JsonProperty("amount") val amount: BigDecimal?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): TransactionHistoryRecord = TransactionHistoryRecord(
        id = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = TransactionHistoryRecord.CHECK_DESCRIPTION,
        checkNumber = this.number,
        amount = amount?.abs()?.negate(),  // a check represents money leaving, so it is always negative. Some statements show it as positive while others negative
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
                amount = recordFields[Keys.AMOUNT]?.currencyValue(),
            )
        }
    }
}