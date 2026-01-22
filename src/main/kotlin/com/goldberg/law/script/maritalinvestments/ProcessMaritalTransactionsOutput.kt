package com.goldberg.law.script.maritalinvestments

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.script.maritalinvestments.model.TransactionLog

data class ProcessMaritalTransactionsOutput @JsonCreator constructor(
    @JsonProperty("holdingsReport") val holdingsReport: Map<String, HoldingsReport>,
    @JsonProperty("transactionLog") val transactionLog: List<TransactionLog>
)