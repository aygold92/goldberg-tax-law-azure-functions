package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.request.AnalyzeDocumentResult.Status
import javax.inject.Inject

data class WriteCsvSummaryResponse @Inject constructor(
    @JsonProperty("status") val status: String,
    @JsonProperty("errorMessage") val errorMessage: String? = null,
    @JsonProperty("recordsFile") val recordsFile: String? = null,
    @JsonProperty("accountSummaryFile") val accountSummaryFile: String? = null,
    @JsonProperty("statementSummaryFile") val statementSummaryFile: String? = null,
    @JsonProperty("checkSummaryFile") val checkSummaryFile: String? = null,
) {
    companion object {
        fun failed(ex: Throwable) = WriteCsvSummaryResponse(Status.FAILED, ex.message)
    }
}