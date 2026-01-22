package com.goldberg.law.script.maritalinvestments

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ProcessMaritalTransactionsInput @JsonCreator constructor(
    @JsonProperty("transactionsCsv") val transactionsCsv: String,
    @JsonProperty("startingHoldings") val startingHoldings: HoldingsReport,
    @JsonProperty("marriageDate") val marriageDate: String,
    @JsonProperty("useCashRatio") val useSplitCashAccounts: Boolean,
)