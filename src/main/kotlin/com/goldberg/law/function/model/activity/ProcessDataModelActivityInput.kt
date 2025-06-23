package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class ProcessDataModelActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("classifiedPdfMetadata") val classifiedPdfMetadata: ClassifiedPdfMetadata,
    @JsonProperty("useOriginalFile") val useOriginalFile: Boolean = false
)