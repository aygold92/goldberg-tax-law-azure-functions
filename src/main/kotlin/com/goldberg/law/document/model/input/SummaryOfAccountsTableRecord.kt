package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.DocumentField
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.currencyValue
import com.goldberg.law.util.last4Digits
import java.math.BigDecimal

data class SummaryOfAccountsTableRecord @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("beginningBalance") val beginningBalance: BigDecimal?,
    @JsonProperty("endingBalance") val endingBalance: BigDecimal?
) {
    object Keys {
        const val ACCOUNT_NUMBER = "Account Number"
        const val BEGINNING_BALANCE = "Beginning Balance"
        const val ENDING_BALANCE = "Ending Balance"
    }
    companion object {
        fun DocumentField.getAccountSummaryRecord(): SummaryOfAccountsTableRecord = this.valueMap.let { accountFields ->
            SummaryOfAccountsTableRecord(
                accountNumber = accountFields[Keys.ACCOUNT_NUMBER]?.valueString?.last4Digits(),
                beginningBalance = accountFields[Keys.BEGINNING_BALANCE]?.currencyValue(),
                endingBalance = accountFields[Keys.ENDING_BALANCE]?.currencyValue(),
            )
        }
    }
}