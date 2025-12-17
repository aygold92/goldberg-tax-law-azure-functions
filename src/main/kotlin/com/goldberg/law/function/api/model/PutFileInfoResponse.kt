package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class PutFileInfoResponse @JsonCreator constructor(
    @JsonProperty("fileId") val fileId: UUID? = null,
    @JsonProperty("status") val status: String? = null,
    @JsonProperty("message") val message: String? = null,
) {
    companion object {
        fun success(fileId: UUID) = PutFileInfoResponse(fileId = fileId)
        
        fun failed(errorMessage: String) = PutFileInfoResponse(status = "Failed", message = errorMessage)
    }
}

