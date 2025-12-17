package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class LoadTransactionsFromModelRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("classificationId") val classificationId: UUID,
)