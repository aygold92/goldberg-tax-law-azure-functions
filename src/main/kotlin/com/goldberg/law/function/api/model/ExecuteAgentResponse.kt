package com.goldberg.law.function.api.model

data class ExecuteAgentResponse(
    val sessionId: String,
    /** False when the classification had already been extracted and the request didn't override it. */
    val started: Boolean = true,
)
