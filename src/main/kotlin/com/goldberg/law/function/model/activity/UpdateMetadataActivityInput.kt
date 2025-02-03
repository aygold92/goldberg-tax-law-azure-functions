package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata
import javax.inject.Inject

// note: this only updates the metadata for input files
data class UpdateMetadataActivityInput @Inject constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("filename") val filename: String,
    @JsonProperty("metadata") val metadata: InputFileMetadata
)