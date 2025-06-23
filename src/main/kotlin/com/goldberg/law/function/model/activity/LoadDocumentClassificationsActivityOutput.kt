package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class LoadDocumentClassificationsActivityOutput @JsonCreator constructor(
    @JsonProperty("classifiedDocumentsMap") val classifiedDocumentsMap: Map<String, List<ClassifiedPdfMetadata>>,
)