package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class DocumentOrchestrationStatus @JsonCreator constructor(
    @JsonProperty("documentName") val documentName: String,
    @JsonProperty("totalPages") val totalPages: Int?,
    @JsonProperty("pagesCompleted") val pagesCompleted: Int?,
)