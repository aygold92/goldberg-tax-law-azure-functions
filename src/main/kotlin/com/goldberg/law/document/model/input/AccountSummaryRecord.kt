package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.model.input.tables.getFieldMapHack
import com.goldberg.law.util.roundToTwoDecimalPlaces

data class AccountSummaryRecord(
    val accountNumber: String?,
    val startPage: Int?,
    val beginningBalance: Double?,
    val endingBalance: Double?
) {
    object Keys {
        const val ACCOUNT = "Account"
        const val START_PAGE = "Start Page"
        const val ACCOUNT_NUMBER = "Account Number"
        const val BEGINNING_BALANCE = "Beginning Balance"
        const val ENDING_BALANCE = "Ending Balance"
    }
    companion object {
        fun DocumentField.getAccountSummaryRecord(): AccountSummaryRecord = this.getFieldMapHack().let { accountFields ->
            AccountSummaryRecord(
                accountNumber = accountFields[Keys.ACCOUNT_NUMBER]?.valueAsString,
                startPage = (accountFields[Keys.START_PAGE]?.value as Number?)?.toInt(),
                beginningBalance = accountFields[Keys.BEGINNING_BALANCE]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                endingBalance = accountFields[Keys.ENDING_BALANCE]?.valueAsDouble?.roundToTwoDecimalPlaces(),
            )
        }
    }
}