package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class CategorizeTransactionsRequest @JsonCreator constructor(
    @JsonProperty("transactions") val transactions: List<String>
)