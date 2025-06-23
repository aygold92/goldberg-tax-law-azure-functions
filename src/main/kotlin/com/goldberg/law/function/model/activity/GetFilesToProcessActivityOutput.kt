package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class GetFilesToProcessActivityOutput @JsonCreator constructor(
    @JsonProperty("filesWithMetadata") val filesWithMetadata: Map<String, InputFileMetadata>
)