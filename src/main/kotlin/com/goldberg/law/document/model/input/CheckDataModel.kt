package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTable.Companion.getCheckImageTable
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.util.*
import java.math.BigDecimal

data class CheckDataModel @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("to") val to: String?,
    @JsonProperty("description") val description: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("amount") val amount: BigDecimal?,
    @JsonProperty("checkEntries") val checkEntries: CheckEntriesTable?,
    @JsonProperty("batesStamp") val batesStamp: String?,
    @JsonProperty("pageMetadata") override val pageMetadata: ClassifiedPdfMetadata
): DocumentDataModel(pageMetadata) {
    @JsonIgnore @Transient
    val transactionDate = fromWrittenDate(date)

    @JsonIgnore
    fun checkDataKey() = CheckDataKey(accountNumber, checkNumber)

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
        fun AnalyzedDocument.toCheckDataModel(classifiedPdfDocument: ClassifiedPdfDocument): CheckDataModel = this.fields.let { documentFields ->
            val accountNumberRead = documentFields[Keys.ACCOUNT_NUMBER]?.valueString
            val checkNumber = documentFields[Keys.CHECK_NUMBER]?.valueAsInt()
            val accountNumber = getAccountNumber(accountNumberRead, checkNumber)

            CheckDataModel(
                accountNumber = accountNumber?.last4Digits(),
                checkNumber = checkNumber,
                to = documentFields[Keys.TO]?.valueString,
                description = documentFields[Keys.DESCRIPTION]?.valueString,
                date = normalizeDate(documentFields[Keys.DATE]?.valueString),
                amount = documentFields[Keys.AMOUNT]?.currencyValue(),
                batesStamp = documentFields[Keys.BATES_STAMP]?.valueString,
                checkEntries = this.getCheckImageTable(),
                pageMetadata = classifiedPdfDocument.toDocumentMetadata()
            )
        }

        // for some checks, we need to get the account number from the bottom of the check,
        // which can get smashed together with the checkNumber, like "8558⑈5563"
        // TODO: account for the case where the check number starts with 0s, like "8558⑈0563" (check number will be 563)
        fun getAccountNumber(accountNumber: String?, checkNumber: Int?): String? = accountNumber?.hackToNumber()?.let { accountNumberRead ->
            if (accountNumberRead.length < 8) return@let accountNumberRead

            val last4 = accountNumberRead.substring(accountNumberRead.length - 4)
            val last8to4 = accountNumberRead.substring(accountNumberRead.length -8, accountNumberRead.length -4)
            if (last4.toInt() == checkNumber) last8to4
            else accountNumberRead
        }

        fun blankModel(classifiedPdfDocument: ClassifiedPdfDocument): CheckDataModel = blankModel(classifiedPdfDocument.toDocumentMetadata())

        fun blankModel(classifiedPdfMetadata: ClassifiedPdfMetadata): CheckDataModel = CheckDataModel(
            null, null, null, null, null, null, null, null,
            classifiedPdfMetadata
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
