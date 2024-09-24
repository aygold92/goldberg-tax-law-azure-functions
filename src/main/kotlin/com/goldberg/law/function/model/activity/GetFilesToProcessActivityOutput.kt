package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class GetFilesToProcessActivityOutput @JsonCreator constructor(
    @JsonProperty("relevantFiles") val relevantFiles: Set<String>
)