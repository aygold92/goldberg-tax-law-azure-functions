package com.goldberg.law.agent.model.output

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming

/** Final output of `bank-statement-splitting`; see `skills/bank-statement-splitting/references/output-schema.md`. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SplitterOutput(
    /** Keyed on `bank_id`; a credit card's id ends in `_cc`. */
    val banks: Map<String, SplitterBank> = emptyMap(),
    /** One per statement, sorted by `start`; ranges never overlap. */
    val boundaries: List<StatementBoundary> = emptyList(),
    val checkPages: List<Int> = emptyList(),
    val unassignedPages: List<Int> = emptyList(),
    val bates: BatesReport? = null,
    val reviewRequired: List<SplitterReviewItem> = emptyList(),
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SplitterBank(
    val name: String?,
    /** `memory` if the bank's patterns came from the memory store, `discovered` if derived this run. */
    val source: String?,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StatementBoundary(
    val start: Int,
    val end: Int,
    val bankId: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BatesReport(
    val sequences: List<BatesSequence> = emptyList(),
    /** Page number → the stamp on that page, for stamped pages outside any sequence. */
    val nonSequenced: Map<Int, String> = emptyMap(),
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class BatesSequence(
    val start: Int,
    val end: Int,
    val firstStamp: String,
    val lastStamp: String,
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class SplitterReviewItem(
    val reason: String,
    val pages: List<Int> = emptyList(),
)
