package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.tables.getFieldMapHack
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.valueAsInt
import java.math.BigDecimal

data class SummaryOfAccountsTableRecord @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("startPage") val startPage: Int?,
    @JsonProperty("beginningBalance") val beginningBalance: BigDecimal?,
    @JsonProperty("endingBalance") val endingBalance: BigDecimal?
) {
    object Keys {
        const val ACCOUNT = "Account"
        const val START_PAGE = "Start Page"
        const val ACCOUNT_NUMBER = "Account Number"
        const val BEGINNING_BALANCE = "Beginning Balance"
        const val ENDING_BALANCE = "Ending Balance"
    }
    companion object {
        fun DocumentField.getAccountSummaryRecord(): SummaryOfAccountsTableRecord = this.getFieldMapHack().let { accountFields ->
            SummaryOfAccountsTableRecord(
                accountNumber = accountFields[Keys.ACCOUNT_NUMBER]?.valueAsString?.last4Digits(),
                startPage = accountFields[Keys.START_PAGE]?.valueAsInt(),
                beginningBalance = accountFields[Keys.BEGINNING_BALANCE]?.currencyValue(),
                endingBalance = accountFields[Keys.ENDING_BALANCE]?.currencyValue(),
            )
        }

        private fun String.last4Digits() = this.filter { str ->
            str.isDigit() }.let {
                if (it.length > 4) it.substring(it.length - 4) else it
            }
    }
}