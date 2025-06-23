package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.pageNumber
import java.math.BigDecimal
import java.util.*

data class TransactionTableAmountRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionHistoryRecord(statementDate: Date?, metadata: ClassifiedPdfMetadata): TransactionHistoryRecord {
        return TransactionHistoryRecord(
            id = this.id,
            date = fromWrittenDateStatementDateOverride(this.date, statementDate),
            description = this.description,
            checkNumber = extractCheckNumber(this.description),
            // a positive entry on the credit card statement is a charge, which means money is going down
            // a negative entry is a credit, which means money goes up
            amount = if (metadata.documentType == DocumentType.CREDIT_CARD) amount?.negate() else amount,
            filePageNumber = metadata.pagesOrdered[page - 1]
        )
    }

    object Keys {
        const val DATE = "Date"
        const val DESCRIPTION = "Description"
        const val AMOUNT = "Amount"
    }

    companion object {
        fun DocumentField.toTransactionTableAmountRecord() = this.valueMap.let { recordFields ->
            TransactionTableAmountRecord(
                date = recordFields[Keys.DATE]?.valueString,
                description = recordFields[Keys.DESCRIPTION]?.valueString,
                amount = recordFields[Keys.AMOUNT]?.currencyValue(),
                page = recordFields.pageNumber()
            )
        }
    }
}