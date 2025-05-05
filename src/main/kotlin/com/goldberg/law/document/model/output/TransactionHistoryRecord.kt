package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toCurrency
import java.math.BigDecimal
import java.util.UUID

@JsonIgnoreProperties(ignoreUnknown = true)
data class TransactionHistoryRecord @JsonCreator constructor(
    @JsonProperty("id")
    val id: String,
    @JsonProperty("date")
    val date: String? = null,
    @JsonProperty("checkNumber")
    val checkNumber: Int? = null,
    @JsonProperty("description")
    val description: String? = null,
    // a positive number represents a positive cash flow (like a check deposit or a credit card refund)
    // a negative number represents a withdrawal or a normal credit card purchase
    // a credit card payment should have two representations: 1 on the bank statement (negative amount as a withdrawal)
    // and one on the credit card statement (positive amount as a statement credit).  This should cancel out, leaving the total spending
    // as just the individual transactions
    @JsonProperty("amount")
    val amount: BigDecimal? = null,
    @JsonProperty("pageMetadata")
    val pageMetadata: TransactionHistoryPageMetadata,
    @JsonProperty("checkDataModel")
    val checkDataModel: CheckDataModel? = null
) {
    @JsonIgnore @Transient
    val transactionDate = fromWrittenDate(date)
    fun toCsv(accountNumber: String?, classification: String, statementDate: String?, filename: String) = listOf(
        date,
        getFinalDescription()?.addQuotes(),
        amount?.toCurrency(),
        "",
        pageMetadata.toCsv(accountNumber, classification, statementDate, filename),
        checkDataModel?.batesStamp?.addQuotes(),
        checkDataModel?.pageMetadata?.toCsv()
    ).joinToString(",") { it ?: "" }

    private fun getFinalDescription(): String? {
        val descriptionFromRecord = if (checkNumber != null && (!hasNumberWithoutCheck() || description == null))
            CHECK_FORMAT.format(checkNumber)
        else if (checkNumber != null)
            "$description ${CHECK_FORMAT.format(checkNumber)}"
        else
            description

        return if (descriptionFromRecord != null && checkDataModel?.getFinalDescription() != null) {
            "$descriptionFromRecord: ${checkDataModel.getFinalDescription()}"
        } else descriptionFromRecord ?: checkDataModel?.getFinalDescription()
    }

    fun withCheckInfo(checkDataModel: CheckDataModel?): TransactionHistoryRecord = if (checkNumber == null || checkDataModel == null) this else {
        TransactionHistoryRecord(id, date, checkNumber, description, amount, pageMetadata, checkDataModel)
    }

    /**
     * CALCULATE SUSPICIOUS REASONS
     */
    @JsonIgnore
    fun getSuspiciousReasons(): List<String> = suspiciousReasonMap.mapNotNull { if (it.first(this)) it.second(this) else null }

    @JsonIgnore
    fun isSuspicious(): Boolean = getSuspiciousReasons().isNotEmpty()
    @JsonIgnore
    fun isDateEmpty() = date == null
    @JsonIgnore
    fun isDescriptionEmpty() = description == null
    @JsonIgnore
    fun isAmountInvalid() =  amount == null || amount == ZERO
    @JsonIgnore
    fun hasCheckWithoutNumber() = hasCheckDescription() && checkNumber == null
    @JsonIgnore
    fun hasNumberWithoutCheck() = checkNumber != null && !hasCheckDescription() && this.description?.lowercase()?.contains("check") != true

    private fun hasCheckDescription() = CHECK_DESCRIPTIONS.any { it.equals(this.description?.trim(), ignoreCase = true) }

    object SuspiciousReasons {
        const val NO_DATE = "Record is missing a date"
        const val NO_DESCRIPTION = "[%s] record is missing a description"
        const val NO_AMOUNT = "[%s] record is missing a transaction amount"
        const val CHECK_WITHOUT_NUMBER = "[%s] Record description says \"check\" but there is no check number"
        const val CHECK_WRONG_DESCRIPTION = "[%s] Record has a check number but the description does not say \"check\""
    }

    companion object {
        const val CHECK_FORMAT = "Check %d"
        const val CHECK_DESCRIPTION = "Check"
        const val CHECK_DESCRIPTION_ALT = "Deposited OR Cashed Check"
        val CHECK_DESCRIPTIONS = listOf(CHECK_DESCRIPTION, CHECK_DESCRIPTION_ALT)

        private val suspiciousReasonMap: List<Pair<(record: TransactionHistoryRecord) -> Boolean, (record: TransactionHistoryRecord) -> String>> = listOf(
            // TODO: consider using record numbers
            Pair(TransactionHistoryRecord::isDateEmpty) { SuspiciousReasons.NO_DATE },
            Pair(TransactionHistoryRecord::isDescriptionEmpty) { record -> SuspiciousReasons.NO_DESCRIPTION.format(record.date) },
            Pair(TransactionHistoryRecord::isAmountInvalid) { record-> SuspiciousReasons.NO_AMOUNT.format(record.date) },
            Pair(TransactionHistoryRecord::hasCheckWithoutNumber) { record-> SuspiciousReasons.CHECK_WITHOUT_NUMBER.format(record.date) },
            Pair(TransactionHistoryRecord::hasNumberWithoutCheck) { record-> SuspiciousReasons.CHECK_WRONG_DESCRIPTION.format(record.date) },
        )

        /** NOTE: BE VERY CAREFUL AS THIS NEEDS TO LINE UP PERFECTLY WITH WHAT IS OUTPUT IN THE .toCsv() METHOD of
         * [com.goldberg.law.document.model.output.TransactionHistoryPageMetadata] and
         * [com.goldberg.law.document.model.output.TransactionHistoryRecord]
         * */
        val CSV_FIELD_HEADERS = listOf(
            "Transaction Date",
            "Description",
            "Amount",
            "",
            "Account",
            "Bates Stamp",
            "Statement Date",
            "Statement Page #",
            "Filename",
            "File Page #",
            "Check Bates Stamp",
            "Check Filename",
            "Check File Page #"
        )
    }
}