package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.pageNumber
import com.goldberg.law.util.valueAsInt
import java.math.BigDecimal
import java.util.*

data class TransactionTableChecksRecord @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("number") val number: Int?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("page") override val page: Int,
): TransactionRecord() {
    override fun toTransactionDetails(statementDate: Date?, classification: Classification): TransactionDetails = TransactionDetails(
        transactionId = this.id,
        date = fromWrittenDateStatementDateOverride(this.date, statementDate),
        description = CHECK_DESCRIPTION,
        checkNumber = this.number,
        amount = amount?.abs()?.negate(),  // a check represents money leaving, so it is always negative. Some statements show it as positive while others negative
        filePageNumber = classification.pagesOrdered[page - 1],
        checkId = null
    )

    object Keys {
        const val DATE = "Date"
        const val NUMBER = "Number"
        const val AMOUNT = "Amount"
    }

    companion object {
        fun DocumentField.toTransactionTableChecksRecord() = this.valueMap.let { recordFields ->
            TransactionTableChecksRecord(
                date = recordFields[Keys.DATE]?.valueString,
                number = recordFields[Keys.NUMBER]?.valueAsInt(),
                amount = recordFields[Keys.AMOUNT]?.currencyValue(),
                page = recordFields.pageNumber()
            )
        }

        const val CHECK_DESCRIPTION = "Check"
    }
}