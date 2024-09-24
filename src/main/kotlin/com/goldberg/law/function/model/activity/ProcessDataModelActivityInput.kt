package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData

data class ProcessDataModelActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("pdfPageData") val pdfPageData: PdfPageData,
    @JsonProperty("overrideTypeClassification") val overrideTypeClassification: String?,
)