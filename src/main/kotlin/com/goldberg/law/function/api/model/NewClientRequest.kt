package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID
import com.google.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class NewClientRequest @Inject constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("requestToken") val requestToken: UUID,
)