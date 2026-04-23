package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class DeleteDocumentRequest(
    @JsonProperty("clientId") val clientId: UUID,
    @JsonProperty("fileId") val fileId: UUID,
)