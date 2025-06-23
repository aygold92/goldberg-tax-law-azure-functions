package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

data class ClassifyDocumentActivityOutput @JsonCreator constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("classifiedDocuments") val classifiedDocuments: List<ClassifiedPdfMetadata>,
)