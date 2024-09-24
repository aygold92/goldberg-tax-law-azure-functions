package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class OrchestrationStatus @JsonCreator constructor(
    @JsonProperty("totalPages") val totalPages: Int,
    @JsonProperty("pagesCompleted") val pagesCompleted: Int,
)