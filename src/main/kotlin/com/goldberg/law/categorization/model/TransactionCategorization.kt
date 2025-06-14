package com.goldberg.law.categorization.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class TransactionCategorization @JsonCreator constructor(
    @JsonProperty("description") val description: String,
    @JsonProperty("vendor") val vendor: String,
    @JsonProperty("category") val category: String,
    @JsonProperty("subcategory") val subcategory: String,
    @JsonProperty("vendConfidence") val vendorConfidence: Int,
    @JsonProperty("catConfidence") val categorizationConfidence: Int,
)