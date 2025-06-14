package com.goldberg.law.document.model.input.tables

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.util.*
import java.math.BigDecimal

data class CheckEntriesTableRow @JsonCreator constructor(
    @JsonProperty("date") val date: String?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("to") val to: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("accountNumber") val accountNumber: String?,
) {
    fun toCheckDataModel(accountNumber: String?, batesStamp: String?, metadata: PdfDocumentPageMetadata): CheckDataModel {
        return CheckDataModel(
            accountNumber = this.accountNumber ?: accountNumber,
            checkEntries = null,
            batesStamp = batesStamp,
            date = this.date,
            checkNumber = checkNumber,
            description = this.description,
            to = to,
            amount = amount,
            pageMetadata = metadata
        )
    }

    object Keys {
        const val CHECK_NUMBER = "CheckNumber"
        const val TO = "To"
        const val DESCRIPTION = "Description"
        const val DATE = "Date"
        const val AMOUNT = "Amount"
        const val ACCOUNT_NUMBER = "Account Number"
    }

    companion object {
        fun DocumentField.toCheckEntriesTableRow() = this.getFieldMapHack().let { recordFields ->
            CheckEntriesTableRow(
                date = normalizeDate(recordFields[Keys.DATE]?.valueAsString),
                checkNumber = recordFields[Keys.CHECK_NUMBER]?.content?.hackToNumber()?.toInt(), // TODO: fix in model
                to = recordFields[Keys.TO]?.valueAsString,
                description = recordFields[Keys.DESCRIPTION]?.valueAsString,
                amount = recordFields[Keys.AMOUNT]?.currencyValue(),
                accountNumber = recordFields[Keys.ACCOUNT_NUMBER]?.valueAsString?.last4Digits(),
            )
        }
    }
}