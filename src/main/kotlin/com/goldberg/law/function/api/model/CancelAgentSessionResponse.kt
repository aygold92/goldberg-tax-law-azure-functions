package com.goldberg.law.function.api.model

data class CancelAgentSessionResponse(
    val sessionId: String,
    /** False when the session had already stopped, so there was nothing to cancel. */
    val interrupted: Boolean,
)
