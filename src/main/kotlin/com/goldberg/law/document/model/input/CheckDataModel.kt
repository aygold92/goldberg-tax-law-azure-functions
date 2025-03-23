package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTable.Companion.getCheckImageTable
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocumentPage
import com.goldberg.law.util.*
import java.math.BigDecimal
import java.util.*

data class CheckDataModel @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("to") val to: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("checkEntries") val checkEntries: CheckEntriesTable?,
    @JsonProperty("batesStamp") val batesStamp: String?,
    @JsonProperty("pageMetadata") override val pageMetadata: PdfDocumentPageMetadata
): DocumentDataModel(pageMetadata) {
    @JsonIgnore @Transient
    val transactionDate = fromWrittenDate(date)

    @JsonIgnore @Transient
    val checkDataKey = CheckDataKey(accountNumber, checkNumber)

    @JsonIgnore
    fun getFinalDescription() = if (this.to != null && this.description != null) "${this.to} - ${this.description}"
    else this.to ?: this.description

    fun toCsv() = listOf(
        accountNumber?.addQuotes(),
        checkNumber,
        description?.addQuotes(),
        date,
        amount?.toCurrency(),
        batesStamp?.addQuotes(),
        pageMetadata.toCsv()
    ).joinToString(",")

    fun matches(record: TransactionHistoryRecord): Boolean {
        // if one of the amounts does not exist, we can probably say it matches
        val amountMatches: Boolean = if (this.amount != null && record.amount != null) this.amount.abs() == record.amount.abs() else true
        return this.checkNumber == record.checkNumber && amountMatches
    }

    fun extractNestedChecks(): List<CheckDataModel> = checkEntries?.images?.map { it.toCheckDataModel(accountNumber, batesStamp, pageMetadata) }
        ?: listOf(this)

    object Keys {
        const val ACCOUNT_NUMBER = "AccountNumber"
        const val CHECK_NUMBER = "CheckNumber"
        const val TO = "To"
        const val DESCRIPTION = "Description"
        const val DATE = "Date"
        const val AMOUNT = "Amount"
        const val CHECK_ENTRIES_TABLE = "Check Entries"
        const val BATES_STAMP = "BatesStamp"
    }
    companion object {
        fun AnalyzedDocument.toCheckDataModel(classifiedPdfDocument: ClassifiedPdfDocumentPage): CheckDataModel = this.fields.let { documentFields ->
            // TODO: add hack for when you get account number + checkNumber
            CheckDataModel(
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueAsString,
                checkNumber = documentFields[Keys.CHECK_NUMBER]?.valueAsInt(),
                to = documentFields[Keys.TO]?.valueAsString,
                description = documentFields[Keys.DESCRIPTION]?.valueAsString,
                date = normalizeDate(documentFields[Keys.DATE]?.valueAsString),
                amount = documentFields[Keys.AMOUNT]?.currencyValue(),
                batesStamp = documentFields[Keys.BATES_STAMP]?.valueAsString,
                checkEntries = this.getCheckImageTable(),
                pageMetadata = classifiedPdfDocument.toDocumentMetadata()
            )
        }

        fun blankModel(classifiedPdfDocument: ClassifiedPdfDocumentPage): CheckDataModel = CheckDataModel(
            null, null, null, null, null, null, null, null,
            classifiedPdfDocument.toDocumentMetadata()
        )

        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD */
        val CSV_FIELD_HEADERS = listOf(
            "Account",
            "Check Number",
            "Description",
            "Date",
            "Amount",
            "Bates Stamp",
            "Filename",
            "File Page #",
        )
    }
}