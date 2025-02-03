package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData

data class LoadAnalyzedModelsActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("pdfPages") val pdfPages: Collection<PdfPageData>
)