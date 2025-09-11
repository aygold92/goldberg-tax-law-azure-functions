package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class UpdateInputFileMetadataRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("filename") val filename: String,
    @JsonProperty("metadata") val metadata: InputFileMetadata
)
