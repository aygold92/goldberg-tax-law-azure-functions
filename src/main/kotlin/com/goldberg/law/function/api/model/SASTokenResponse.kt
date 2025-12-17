package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class SASTokenResponse @Inject constructor(
    @JsonProperty("token") val token: String
)