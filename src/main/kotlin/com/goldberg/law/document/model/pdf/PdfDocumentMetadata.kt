package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.InputFileMetadata
import javax.inject.Inject

data class PdfDocumentMetadata @Inject constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("metadata") val metadata: InputFileMetadata
)