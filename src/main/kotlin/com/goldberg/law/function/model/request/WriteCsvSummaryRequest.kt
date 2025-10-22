package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class StatementRequest @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("classification") val classification: String,
    @JsonProperty("date") val date: String?,
    @JsonProperty("filenameWithPages") val filenameWithPages: String? = null,
)

data class WriteCsvSummaryRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("statementKeys") val statementKeys: List<StatementRequest>,
    @JsonProperty("outputDirectory") val outputDirectory: String? = null,
    @JsonProperty("outputFile") val outputFile: String? = null,
)