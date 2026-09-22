package com.goldberg.law.function.api.model

data class ExecuteSplitterAgentResponse(
    val sessionId: String,
    val anthropicFileId: String?,
    /** False when the file had already been split and the request didn't override it. */
    val started: Boolean = true,
)
