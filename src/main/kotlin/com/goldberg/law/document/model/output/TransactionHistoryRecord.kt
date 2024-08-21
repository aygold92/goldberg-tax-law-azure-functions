package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import com.goldberg.law.util.toCurrency
import java.util.Date

// TODO: add ending daily balances to each record to verify accuracy of numbers
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
) {
    fun toCsv() = listOf(
        date?.toTransactionDate(),
        getFinalDescription()?.addQuotes(),
        amount?.toCurrency(),
    ).joinToString(",") { it?.toString() ?: "" }

    private fun getFinalDescription(): String? =
        if (checkNumber != null && !hasNumberWithoutCheck())
            CHECK_FORMAT.format(checkNumber)
        else if (checkNumber != null)
            description + (CHECK_FORMAT.format(checkNumber))
        else
            description

    /**
     * CALCULATE SUSPICIOUS REASONS
     */
    private val suspiciousReasonMap: List<Pair<() -> Boolean, String>> = listOf(
        // TODO: consider using record numbers
        Pair(::isDateEmpty, SuspiciousReasons.NO_DATE),
        Pair(::isDescriptionEmpty, SuspiciousReasons.NO_DESCRIPTION.format(date?.toTransactionDate())),
        Pair(::isAmountInvalid, SuspiciousReasons.ONE_OF_WITHDRAWAL_DEPOSIT.format(date?.toTransactionDate())),
        Pair(::hasCheckWithoutNumber, SuspiciousReasons.CHECK_WITHOUT_NUMBER.format(date?.toTransactionDate())),
        Pair(::hasNumberWithoutCheck, SuspiciousReasons.CHECK_WRONG_DESCRIPTION.format(date?.toTransactionDate()))
    )
    val isSuspiciousReasons: List<String> = suspiciousReasonMap.mapNotNull { if (it.first()) it.second else null }

    val isSuspicious: Boolean = isSuspiciousReasons.isNotEmpty()
    fun isDateEmpty() = date == null
    fun isDescriptionEmpty() = description == null
    fun isAmountInvalid() =  amount == null || amount == 0.0
    fun hasCheckWithoutNumber() = description?.lowercase()?.trim() == "check" && checkNumber == null
    fun hasNumberWithoutCheck() = checkNumber != null && description?.lowercase()?.trim() != "check"

    object SuspiciousReasons {
        const val NO_DATE = "Record has no date"
        const val NO_DESCRIPTION = "[%s] record has no description"
        const val ONE_OF_WITHDRAWAL_DEPOSIT = "[%s] Could not find deposit/withdrawal amount"
        const val CHECK_WITHOUT_NUMBER = "[%s] Record description says \"check\" but there is no check number"
        const val CHECK_WRONG_DESCRIPTION = "[%s] Record has a check number but the description does not say \"check\""
    }

    companion object {
        const val CHECK_FORMAT = "Check %d"
        const val CHECK_DESCRIPTION = "Check"
    }
}