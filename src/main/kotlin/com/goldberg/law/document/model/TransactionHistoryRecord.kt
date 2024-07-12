package com.goldberg.law.document.model

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toTransactionDate
import com.goldberg.law.util.toCurrency
import java.util.Date

// TODO: add ending daily balances to each record to verify accuracy of numbers
data class TransactionHistoryRecord(
    val date: Date? = null,
    val checkNumber: Int? = null,
    val description: String? = null,
    val depositAmount: Double? = null,
    val withdrawalAmount: Double? = null
) {
    private val suspiciousReasonMap: List<Pair<() -> Boolean, String>> = listOf(
        Pair(::isDateEmpty, "Record has no date"),
        Pair(::isDescriptionEmpty, "Record with date $date has no description"),
        Pair(::isAmountInvalid, "Exactly one of deposit/withdrawal must be specified"),
        Pair(::hasCheckWithoutNumber, "Record description says \"check\" but there is no check number"),
        Pair(::hasNumberWithoutCheck, "Record has a check number but the description does not say \"check\"")
    )
    val isSuspiciousReasons: List<String> = suspiciousReasonMap.mapNotNull { if (it.first()) it.second else null }
    val isSuspicious: Boolean = isSuspiciousReasons.isNotEmpty()

    fun isDateEmpty() = date == null
    fun isDescriptionEmpty() = description == null
    fun isAmountInvalid() =  listOfNotNull(withdrawalAmount, depositAmount).size != 1
    fun hasCheckWithoutNumber() = description?.lowercase()?.trim() == "check" && checkNumber == null
    fun hasNumberWithoutCheck() = checkNumber != null && description?.lowercase()?.trim() != "check"

    fun toCsv() = listOf(
        date?.toTransactionDate(),
        checkNumber,
        description?.addQuotes(),
        depositAmount?.toCurrency(),
        withdrawalAmount?.toCurrency()
    ).joinToString(",") { it?.toString() ?: "" }

    fun fromAzureField(fieldMap: Map<String, DocumentField>) {

    }

    companion object {
        const val DATE = "Date"
        const val CHECK_NUMBER = "Check Number"
        const val DESCRIPTION = "Description"
        const val WITHDRAWALS_SUBTRACTIONS = "Withdrawals/ Subtractions"
        const val DEPOSITS_ADDITIONS = "Deposits/ Additions"
    }
}