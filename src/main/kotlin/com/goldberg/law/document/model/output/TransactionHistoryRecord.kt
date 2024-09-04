package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import com.goldberg.law.util.toCurrency
import java.util.Date

data class TransactionHistoryRecord(
    val date: Date? = null,
    val checkNumber: Int? = null,
    val description: String? = null,
    // a positive number represents a positive cash flow (like a check deposit or a credit card refund)
    // a negative number represents a withdrawal or a normal credit card purchase
    // a credit card payment should have two representations: 1 on the bank statement (negative amount as a withdrawal)
    // and one on the credit card statement (positive amount as a statement credit).  This should cancel out, leaving the total spending
    // as just the individual transactions
    val amount: Double? = null,
    val pageMetadata: TransactionHistoryPageMetadata,
    val checkData: CheckData? = null
) {
    fun toCsv(accountNumber: String?, classification: String) = listOf(
        date?.toTransactionDate(),
        getFinalDescription()?.addQuotes(),
        amount?.toCurrency(),
        "",
        pageMetadata.toCsv(accountNumber, classification),
        checkData?.pageMetadata?.toCsv()
    ).joinToString(",") { it ?: "" }

    private fun getFinalDescription(): String? =
        if (checkNumber != null && !hasNumberWithoutCheck())
            CHECK_FORMAT.format(checkNumber)
        else if (checkNumber != null)
            "$description ${CHECK_FORMAT.format(checkNumber)}"
        else
            description

    fun withCheckInfo(checkData: CheckData): TransactionHistoryRecord = if (checkNumber == null) this else {
        TransactionHistoryRecord(date, checkNumber, description, amount, pageMetadata, checkData)
    }

    /**
     * CALCULATE SUSPICIOUS REASONS
     */
    fun getSuspiciousReasons(): List<String> = suspiciousReasonMap.mapNotNull { if (it.first(this)) it.second(this) else null }

    fun isSuspicious(): Boolean = getSuspiciousReasons().isNotEmpty()
    fun isDateEmpty() = date == null
    fun isDescriptionEmpty() = description == null
    fun isAmountInvalid() =  amount == null || amount == 0.0
    fun hasCheckWithoutNumber() = description?.lowercase()?.trim() == "check" && checkNumber == null
    fun hasNumberWithoutCheck() = checkNumber != null && description?.lowercase()?.trim() != "check"

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

        private val suspiciousReasonMap: List<Pair<(record: TransactionHistoryRecord) -> Boolean, (record: TransactionHistoryRecord) -> String>> = listOf(
            // TODO: consider using record numbers
            Pair(TransactionHistoryRecord::isDateEmpty) { SuspiciousReasons.NO_DATE },
            Pair(TransactionHistoryRecord::isDescriptionEmpty) { record -> SuspiciousReasons.NO_DESCRIPTION.format(record.date?.toTransactionDate()) },
            Pair(TransactionHistoryRecord::isAmountInvalid) { record-> SuspiciousReasons.NO_AMOUNT.format(record.date?.toTransactionDate()) },
            Pair(TransactionHistoryRecord::hasCheckWithoutNumber) { record-> SuspiciousReasons.CHECK_WITHOUT_NUMBER.format(record.date?.toTransactionDate()) },
            Pair(TransactionHistoryRecord::hasNumberWithoutCheck) { record-> SuspiciousReasons.CHECK_WRONG_DESCRIPTION.format(record.date?.toTransactionDate()) }
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