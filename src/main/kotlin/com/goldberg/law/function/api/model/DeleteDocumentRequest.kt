package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class DeleteDocumentRequest(
    @JsonProperty("clientID") val clientID: UUID,
    @JsonProperty("fileId") val fileId: UUID,
)