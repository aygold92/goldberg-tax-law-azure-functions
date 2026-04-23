package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class FetchWriteSASTokensResponse @Inject constructor(
    @JsonProperty("tokens") val tokens: Map<String, SASTokenResult>,
    @JsonProperty("alreadyExist") val alreadyExist: Set<String>,
)