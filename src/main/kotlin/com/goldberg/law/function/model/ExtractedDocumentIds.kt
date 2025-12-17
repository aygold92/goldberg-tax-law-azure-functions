package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class ExtractedDocumentIds @JsonCreator constructor(
    @JsonProperty("statementIds") val statementIds: Set<UUID> = emptySet(),
    @JsonProperty("checkIds") val checkIds: Set<UUID> = emptySet(),
) {
    fun isEmpty() = statementIds.isEmpty() && checkIds.isEmpty()
    fun hasChecks() = checkIds.isNotEmpty()
    fun hasStatements() = statementIds.isNotEmpty()
}