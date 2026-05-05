package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*
import com.google.inject.Inject

data class AnalyzePagesRequest @Inject constructor(
    @JsonProperty("pageRequests") val pageRequests: Set<UUID>,
)