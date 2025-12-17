package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class GetDocumentDataModelRequest @JsonCreator constructor(
    @JsonProperty("classificationId") val classificationId: UUID,
)