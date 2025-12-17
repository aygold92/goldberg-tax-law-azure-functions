package com.goldberg.law.function.activity.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification
import java.util.*

data class ClassifyDocumentActivityOutput @JsonCreator constructor(
    @JsonProperty("fileId") val fileId: UUID,
    @JsonProperty("classifications") val classifications: List<Classification>,
)