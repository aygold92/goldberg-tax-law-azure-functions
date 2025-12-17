package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class PutFileInfoRequest @JsonCreator constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("storageLocation") val storageLocation: String,
    @JsonProperty("clientId") val clientId: UUID,
    @JsonProperty("requestToken") val requestToken: UUID
)

