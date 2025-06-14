package com.goldberg.law.categorization.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TransactionVendor @JsonCreator constructor(
    @JsonProperty("description") val description: String,
    @JsonProperty("vendor") val vendor: String,
    @JsonProperty("confidence") val confidence: Int
)