package com.goldberg.law.categorization.chatgbt

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class CategorizeTransactionResponse @JsonCreator constructor(
    @JsonProperty("v") val v: String, // vendor
    @JsonProperty("catA") val catA: String,
    @JsonProperty("catB") val catB: String,
    @JsonProperty("cc") val cc: Int, // confidence categorization
    @JsonProperty("cv") val cv: Int, // confidence vendor
)