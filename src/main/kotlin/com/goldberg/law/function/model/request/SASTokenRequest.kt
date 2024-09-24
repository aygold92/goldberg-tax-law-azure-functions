package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class SASTokenRequest @Inject constructor(
    @JsonProperty("token") val token: String
)