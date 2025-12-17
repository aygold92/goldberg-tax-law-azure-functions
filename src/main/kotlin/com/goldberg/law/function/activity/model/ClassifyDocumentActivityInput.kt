package com.goldberg.law.function.activity.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.InputFile

data class ClassifyDocumentActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("inputFile") val inputFile: InputFile,
)