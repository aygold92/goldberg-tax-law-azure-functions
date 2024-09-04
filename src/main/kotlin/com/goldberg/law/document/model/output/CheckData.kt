package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.toCurrency
import com.goldberg.law.util.toTransactionDate
import java.util.*
import kotlin.math.abs

data class CheckData(
    val accountNumber: String?,
    val checkNumber: Int?,
    val description: String?,
    val date: Date?,
    val amount: Double?,
    val pageMetadata: PageMetadata
) {
    val checkDataKey = CheckDataKey(accountNumber, checkNumber)

    fun toCsv() = listOf(
        accountNumber?.addQuotes(),
        checkNumber,
        description?.addQuotes(),
        date?.toTransactionDate(),
        amount?.toCurrency(),
        pageMetadata.toCsv()
    ).joinToString(",")

    fun matches(record: TransactionHistoryRecord): Boolean {
        // if one of the amounts does not exist, we can probably say it matches
        val amountMatches: Boolean = if (this.amount != null && record.amount != null) abs(this.amount) == abs(record.amount) else true
        return this.checkNumber == record.checkNumber && this.date == record.date &&
                amountMatches
    }

    companion object {
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