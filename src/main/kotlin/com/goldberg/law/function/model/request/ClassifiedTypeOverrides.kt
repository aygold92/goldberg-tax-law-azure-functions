package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ClassifiedTypeOverrides @JsonCreator constructor(
    @JsonProperty("fileName") val fileName: String,
    @JsonProperty("typeOverrides") val typesOverrides: Set<TypeOverride>
)

data class TypeOverride @JsonCreator constructor(
    @JsonProperty("type") val type: String,
    @JsonProperty("pages") val pages: Set<Int>,
)