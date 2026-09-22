package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

/**
 * Either a [classificationId] — the splitter's page range for one statement, which carries everything the
 * agent needs and gets the session recorded against it — or the raw parameters, which start a one-off run
 * that isn't tied to any record.
 */
data class ExecuteExtractionAgentRequest(
    val classificationId: UUID? = null,
    val anthropicFileId: String? = null,
    val fileName: String? = null,
    val startPage: Int? = null,
    val endPage: Int? = null,
    val bankId: String? = null,
    val checkPages: List<Int> = emptyList(),
    /** Extract again even though an extraction session is already recorded against the classification. */
    @JsonProperty("override") val overrideExisting: Boolean = false,
) {
    fun requireRawParameters(): RawExtractionParameters {
        require(anthropicFileId != null && fileName != null && startPage != null && endPage != null && bankId != null) {
            "Pass either a classificationId or all of anthropicFileId, fileName, startPage, endPage and bankId"
        }
        return RawExtractionParameters(anthropicFileId, fileName, startPage, endPage, bankId, checkPages)
    }

    data class RawExtractionParameters(
        val anthropicFileId: String,
        val fileName: String,
        val startPage: Int,
        val endPage: Int,
        val bankId: String,
        val checkPages: List<Int>,
    )
}
