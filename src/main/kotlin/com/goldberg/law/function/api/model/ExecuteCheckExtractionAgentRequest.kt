package com.goldberg.law.function.api.model

data class ExecuteCheckExtractionAgentRequest(
    val anthropicFileId: String,
    val pages: List<Int>,
)
