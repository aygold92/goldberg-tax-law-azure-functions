package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class SASTokenRequest @Inject constructor(
    @JsonProperty("clientId") val clientId: String,
)