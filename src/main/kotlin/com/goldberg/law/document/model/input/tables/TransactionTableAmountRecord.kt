package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.currencyValue
import java.math.BigDecimal
import java.util.*

data class TransactionTableAmountRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: TransactionHistoryPageMetadata, documentType: DocumentType): TransactionHistoryRecord {
        return TransactionHistoryRecord(
            id = this.id,
            date = fromWrittenDateStatementDateOverride(this.date, statementDate),
            description = this.description,
            checkNumber = extractCheckNumber(this.description),
            // a positive entry on the credit card statement is a charge, which means money is going down
            // a negative entry is a credit, which means money goes up
            amount = if (documentType == DocumentType.CREDIT_CARD) amount?.negate() else amount,
            pageMetadata = metadata
        )
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
                amount = recordFields[Keys.AMOUNT]?.currencyValue()
            )
        }
    }
}