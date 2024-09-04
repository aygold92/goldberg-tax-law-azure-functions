package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.output.CheckData
import com.goldberg.law.document.model.output.PageMetadata
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.valueAsInt
import java.util.*

data class CheckDataModel(
    val accountNumber: String?,
    val checkNumber: Int?,
    val to: String?,
    val description: String?,
    val date: Date?,
    val amount: Double?,
    val batesStamp: String?
) {

    fun toCheckData(pageMetadata: PageMetadata): CheckData {
        val finalDescription = if (this.to != null && this.description != null) "${this.to} - ${this.description}"
        else this.to ?: this.description
        return CheckData(accountNumber, checkNumber, finalDescription, date, amount, pageMetadata)
    }

    object Keys {
        const val ACCOUNT_NUMBER = "AccountNumber"
        const val CHECK_NUMBER = "CheckNumber"
        const val TO = "To"
        const val DESCRIPTION = "Description"
        const val DATE = "Date"
        const val AMOUNT = "Amount"
        const val BATES_STAMP = "BatesStamp"
    }
    companion object {
        // TODO: there could be multiple checks on one page?
        fun AnalyzedDocument.toCheckDataModel(): CheckDataModel = this.fields.let { documentFields ->
            CheckDataModel(
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueAsString,
                checkNumber = documentFields[Keys.CHECK_NUMBER]?.valueAsInt(),
                to = documentFields[Keys.TO]?.valueAsString,
                description = documentFields[Keys.DESCRIPTION]?.valueAsString,
                date = fromWrittenDate(documentFields[Keys.DATE]?.valueAsString),
                amount = documentFields[Keys.AMOUNT]?.valueAsDouble,
                batesStamp = documentFields[Keys.BATES_STAMP]?.valueAsString
            )
        }
    }
}