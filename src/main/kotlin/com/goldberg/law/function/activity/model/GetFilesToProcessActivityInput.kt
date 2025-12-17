package com.goldberg.law.function.activity.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class GetFilesToProcessActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("fileIds") val fileIds: Set<UUID>,
)