package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class PutDocumentClassificationRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("classification") val classification: List<ClassifiedPdfMetadata>,
    @JsonProperty("overwriteAll") val overwriteAll: Boolean,
)