package com.goldberg.law.function.api.model

data class ExecuteExtractionAgentRequest(
    val anthropicFileId: String,
    val fileName: String,
    val startPage: Int,
    val endPage: Int,
    val bankId: String,
    val checkPages: List<Int> = emptyList(),
)
