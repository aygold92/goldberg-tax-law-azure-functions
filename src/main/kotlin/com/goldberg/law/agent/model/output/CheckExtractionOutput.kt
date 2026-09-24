package com.goldberg.law.agent.model.output

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.time.LocalDate

/** Final output of `check-extraction`; see `skills/check-extraction/references/output-schema.md`. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class CheckExtractionOutput(
    /** In page order, and in reading order within a page. */
    val checks: List<ExtractedCheck> = emptyList(),
    /** Pages that were looked at and held no checks. */
    val pagesWithNoChecks: List<Int> = emptyList(),
    /** Pages believed to hold checks that couldn't be read at all. */
    val unreadablePages: List<Int> = emptyList(),
)

/** `page` is always present; every other field may be null. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExtractedCheck(
    val page: Int,
    val checkNo: Int?,
    val acct: String?,
    val date: LocalDate?,
    val amt: BigDecimal?,
    val payee: String?,
    val memo: String?,
    val reviewRequired: Boolean = false,
)
