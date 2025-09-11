package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class UpdateInputFileMetadataResponse @JsonCreator constructor(
    @JsonProperty("status") val status: String,
    @JsonProperty("message") val message: String,
    @JsonProperty("updatedMetadata") val updatedMetadata: InputFileMetadata?
) {
    companion object {
        fun success(metadata: InputFileMetadata) = UpdateInputFileMetadataResponse(
            status = "Success",
            message = "Metadata updated successfully",
            updatedMetadata = metadata
        )
        
        fun failed(errorMessage: String) = UpdateInputFileMetadataResponse(
            status = "Failed",
            message = errorMessage,
            updatedMetadata = null
        )
    }
}
