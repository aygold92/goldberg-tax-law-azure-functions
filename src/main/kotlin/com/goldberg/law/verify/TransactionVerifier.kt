package com.goldberg.law.verify

import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.monthsFromNow
import com.google.inject.Inject
import java.util.*

class TransactionVerifier @Inject constructor() {

    fun getSuspiciousReasons(statementDate: Date?, transaction: TransactionDetails): List<String> = mutableListOf<String>().apply {
        if (transaction.isDateEmpty()) add(SuspiciousReasons.NO_DATE)
        if (transaction.isDescriptionEmpty()) add(SuspiciousReasons.NO_DESCRIPTION.format(transaction.date))
        if (transaction.isAmountInvalid()) add(SuspiciousReasons.NO_AMOUNT.format(transaction.date))
        if (transaction.hasCheckWithoutNumber()) add(SuspiciousReasons.CHECK_WITHOUT_NUMBER.format(transaction.date))
        // TODO: this should be a warning
        // if (transaction.hasNumberWithoutCheck()) add(SuspiciousReasons.CHECK_WRONG_DESCRIPTION.format(transaction.date))
        if (statementDate != null && transaction.isOutsideStatementRange(statementDate)) add(SuspiciousReasons.DATE_OUTSIDE_STATEMENT.format(transaction.date))
    }

    private fun TransactionDetails.isDateEmpty() = date == null
    private fun TransactionDetails.isDescriptionEmpty() = description == null
    private fun TransactionDetails.isAmountInvalid() =  amount == null && description?.lowercase()?.startsWith("interest rate change from") != true
    private fun TransactionDetails.hasCheckWithoutNumber() = hasCheckDescription() && checkNumber == null
    private fun TransactionDetails.isOutsideStatementRange(statementDate: Date) = transactionDate.let { td ->
        td != null && (td.after(statementDate) || td.before(statementDate.monthsFromNow(-2)))
    }

    object SuspiciousReasons {
        const val NO_DATE = "Record is missing a date"
        const val DATE_OUTSIDE_STATEMENT = "Date [%s] is outside statement range"
        const val NO_DESCRIPTION = "[%s] record is missing a description"
        const val NO_AMOUNT = "[%s] record is missing a transaction amount"
        const val CHECK_WITHOUT_NUMBER = "[%s] Record description says \"check\" but there is no check number"
        const val CHECK_WRONG_DESCRIPTION = "[%s] Record has a check number but the description does not say \"check\""
    }
}