package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData

data class SplitPdfActivityOutput @JsonCreator constructor(
    @JsonProperty("pdfPages") val pdfPages: Set<PdfPageData>
)