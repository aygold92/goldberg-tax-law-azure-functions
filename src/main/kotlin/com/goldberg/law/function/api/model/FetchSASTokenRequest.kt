package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID
import javax.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchSASTokenRequest @Inject constructor(
    @JsonProperty("clientId") val clientId: UUID,
)
