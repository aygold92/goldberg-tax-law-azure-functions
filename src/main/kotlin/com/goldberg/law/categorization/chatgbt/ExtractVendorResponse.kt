package com.goldberg.law.categorization.chatgbt

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ExtractVendorResponse @JsonCreator constructor(
    @JsonProperty("v") val v: String, // vendor
    @JsonProperty("c") val c: Int, // confidence
)