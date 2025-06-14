package com.goldberg.law.categorization.chatgbt

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class CategorizeVendorResponse @JsonCreator constructor(
    @JsonProperty("catA") val catA: String,
    @JsonProperty("catB") val catB: String,
    @JsonProperty("c") val c: Int, // confidence categorization
)