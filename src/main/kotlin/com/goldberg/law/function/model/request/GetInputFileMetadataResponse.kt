package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class GetInputFileMetadataResponse @JsonCreator constructor(
    @JsonProperty("status") val status: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("metadata") val metadata: InputFileMetadata?
) {
    companion object {
        fun success(metadata: InputFileMetadata) = GetInputFileMetadataResponse(
            status = "Success",
            message = "Metadata retrieved successfully",
            metadata = metadata
        )
        
        fun failed(errorMessage: String) = GetInputFileMetadataResponse(
            status = "Failed",
            message = errorMessage,
            metadata = null
        )
    }
}
