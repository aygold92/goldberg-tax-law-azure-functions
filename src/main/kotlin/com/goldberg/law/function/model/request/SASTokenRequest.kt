package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class SASTokenRequest @Inject constructor(
    @JsonProperty("action") val action: String,
)