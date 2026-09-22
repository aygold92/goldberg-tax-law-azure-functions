package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Either a [classificationId] — the splitter's check pages, which gets the session recorded against it — or
 * the raw parameters, which start a one-off run that isn't tied to any record.
 */
data class ExecuteCheckExtractionAgentRequest(
    val classificationId: UUID? = null,
    val anthropicFileId: String? = null,
    val pages: List<Int> = emptyList(),
    /** Extract again even though an extraction session is already recorded against the classification. */
    @JsonProperty("override") val overrideExisting: Boolean = false,
) {
    fun requireRawParameters(): Pair<String, List<Int>> {
        require(anthropicFileId != null && pages.isNotEmpty()) {
            "Pass either a classificationId or both anthropicFileId and pages"
        }
        return anthropicFileId to pages
    }
}
