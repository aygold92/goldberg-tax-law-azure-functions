package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class MatchStatementsWithChecksRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("clientId") val clientId: UUID,
    @JsonProperty("transactionCheckMatches") val transactionCheckMatches: List<TransactionCheckMatch>,
)

data class TransactionCheckMatch(
    @JsonProperty("transactionId") val transactionId: UUID,
    @JsonProperty("checkId") val checkId: UUID,
)

