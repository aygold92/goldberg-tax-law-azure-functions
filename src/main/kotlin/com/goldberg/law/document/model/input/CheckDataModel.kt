package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTable.Companion.getCheckImageTable
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.Classification
import com.goldberg.law.util.*
import java.math.BigDecimal
import java.util.*

data class CheckDataModel(
    val accountNumber: String?,
    val checkNumber: Int?,
    val to: String?,
    val description: String?,
    val date: String?,
    val amount: BigDecimal?,
    val checkEntries: CheckEntriesTable?,
    val batesStamp: String?,
    override val classification: Classification
): DocumentDataModel(classification) {
    @JsonIgnore @Transient
    val transactionDate = fromWrittenDate(date)

    fun toCheckDetails(): List<CheckDetails> = checkEntries?.images?.map { it.toCheckDetails(accountNumber, batesStamp) }
        ?: listOf(
            CheckDetails(
                checkId = UUID.randomUUID(),
                accountNumber = accountNumber,
                batesStamp = batesStamp,
                date = date,
                checkNumber = checkNumber,
                description = description,
                to = to,
                amount = amount,
            )
        )

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
        fun AnalyzedDocument.toCheckDataModel(classification: Classification): CheckDataModel = this.fields.let { documentFields ->
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
                classification = classification
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

        fun blankModel(classification: Classification): CheckDataModel = CheckDataModel(
            null, null, null, null, null, null, null, null,
            classification
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
