package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID
import com.google.inject.Inject

@JsonIgnoreProperties(ignoreUnknown = true)
data class FetchWriteSASTokensRequest @Inject constructor(
    @JsonProperty("clientId") val clientId: UUID,
    @JsonProperty("filenames") val filenames: List<String>,
)
