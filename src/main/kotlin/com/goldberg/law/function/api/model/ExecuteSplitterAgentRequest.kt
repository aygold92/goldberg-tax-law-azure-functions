package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class ExecuteSplitterAgentRequest(
    val fileId: UUID,
    /** Split the file again even though a splitter session is already recorded against it. */
    @JsonProperty("override") val overrideExisting: Boolean = false,
)
