package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class NewClientRequest @Inject constructor(
    @JsonProperty("clientName") val clientName: String,
)