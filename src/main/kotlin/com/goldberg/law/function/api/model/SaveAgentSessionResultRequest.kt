package com.goldberg.law.function.api.model

data class SaveAgentSessionResultRequest(
    val sessionId: String,
    /**
     * Saving replaces the session target's existing records, so by default a target that already holds them is
     * left untouched — including any manual edits. Set this to overwrite them with this session's output.
     */
    val override: Boolean = false,
)
