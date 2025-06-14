package com.goldberg.law.categorization.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class VendorCategorization @JsonCreator constructor(
    @JsonProperty("vendor") val vendor: String,
    @JsonProperty("category") val category: String,
    @JsonProperty("subcategory") val subcategory: String,
    @JsonProperty("confidence") val confidence: Int,
)