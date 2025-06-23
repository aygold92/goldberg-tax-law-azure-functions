package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toTransactionDate
import java.util.Date

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionHistoryPageMetadata @JsonCreator constructor(
    @JsonProperty("filePageNumber")
    val filePageNumber: Int,
    @JsonProperty("batesStamp")
    val batesStamp: String? = null, // ID number at the bottom of every submitted page, for tracking purposes
) {
    fun toCsv(accountNumber: String?, classification: String, date: String?, filename: String) = listOf(
        joinAccountNumber(accountNumber, classification), batesStamp?.addQuotes(),
        date,
        filename.addQuotes(), filePageNumber
    ).joinToString(",") { it?.toString() ?: "" }

    companion object {
        private const val UNKNOWN_ACCOUNT = "Unknown"

        fun joinAccountNumber(accountNumber: String?, classification: String) =
            "$classification - ${accountNumber ?: UNKNOWN_ACCOUNT}".addQuotes()
    }
}