package com.goldberg.law.document.model.input.tables

import com.azure.ai.documentintelligence.models.DocumentField
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.util.*
import java.math.BigDecimal
import java.util.*

data class CheckEntriesTableRow(
    val date: String?,
    val checkNumber: Int?,
    val to: String?,
    val description: String?,
    val amount: BigDecimal?,
    val accountNumber: String?,
    val page: Int,
) {
    fun toCheckDetails(accountNumber: String?, batesStamp: String?): CheckDetails {
        return CheckDetails(
            checkId = UUID.randomUUID(),
            accountNumber = this.accountNumber ?: accountNumber,
            batesStamp = batesStamp,
            date = this.date,
            checkNumber = checkNumber,
            description = this.description,
            to = to,
            amount = amount,
        )
    }

    object Keys {
        const val CHECK_NUMBER = "CheckNumber"
        const val TO = "To"
        const val DESCRIPTION = "Description"
        const val DATE = "Date"
        const val AMOUNT = "Amount"
        const val ACCOUNT_NUMBER = "Account Number"
    }

    companion object {
        fun DocumentField.toCheckEntriesTableRow() = this.valueMap.let { recordFields ->
            // in case we forget to label the check field as an int
            val checkNumber = recordFields[Keys.CHECK_NUMBER]?.valueInteger?.toInt()
                ?: recordFields[Keys.CHECK_NUMBER]?.valueNumber?.toInt()
                ?: recordFields[Keys.CHECK_NUMBER]?.content?.hackToNumber()?.toInt()
            CheckEntriesTableRow(
                date = normalizeDate(recordFields[Keys.DATE]?.valueString),
                checkNumber = checkNumber,
                to = recordFields[Keys.TO]?.valueString,
                description = recordFields[Keys.DESCRIPTION]?.valueString,
                amount = recordFields[Keys.AMOUNT]?.currencyValue(),
                accountNumber = recordFields[Keys.ACCOUNT_NUMBER]?.valueString?.last4Digits(),
                page = recordFields.pageNumber()
            )
        }
    }
}