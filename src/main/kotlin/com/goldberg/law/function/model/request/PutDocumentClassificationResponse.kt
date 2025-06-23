package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class PutDocumentClassificationResponse @JsonCreator constructor(
    @JsonProperty("classificationData") val classificationData: List<ClassifiedPdfMetadata>
)