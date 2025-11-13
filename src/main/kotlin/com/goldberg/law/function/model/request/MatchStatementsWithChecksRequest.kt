package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.PdfMetadata

data class MatchStatementsWithChecksRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("statements") val statements: List<PdfMetadata>,
    @JsonProperty("checks") val checks: List<PdfMetadata>,
)

