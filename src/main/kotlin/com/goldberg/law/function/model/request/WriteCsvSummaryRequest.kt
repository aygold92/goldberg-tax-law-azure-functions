package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class WriteCsvSummaryRequest @JsonCreator constructor(
    @JsonProperty("statementKeys") val statementKeys: Set<String>,
    @JsonProperty("outputDirectory") val outputDirectory: String = "",
    @JsonProperty("outputFile") val outputFile: String? = null,
)