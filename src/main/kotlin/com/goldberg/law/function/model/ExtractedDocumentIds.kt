package com.goldberg.law.function.model

import java.util.*

data class ExtractedDocumentIds(
    val statementIds: Set<UUID> = emptySet(),
    val checkIds: Set<UUID> = emptySet(),
) {
    fun isEmpty() = statementIds.isEmpty() && checkIds.isEmpty()
    fun hasChecks() = checkIds.isNotEmpty()
    fun hasStatements() = statementIds.isNotEmpty()
}