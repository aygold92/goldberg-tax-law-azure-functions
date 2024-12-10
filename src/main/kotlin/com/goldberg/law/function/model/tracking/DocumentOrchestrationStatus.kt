package com.goldberg.law.function.model.tracking

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class DocumentOrchestrationStatus @JsonCreator constructor(
    @JsonProperty("fileName") val fileName: String,
    @JsonProperty("totalPages") val totalPages: Int?,
    @JsonProperty("pagesCompleted") val pagesCompleted: Int?,
    @JsonProperty("metadata") val metadata: InputFileMetadata?
)