package com.goldberg.law.function.model.updatemodel

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata

data class OverrideModelDetails(
    @JsonProperty("transactions") val transactions: Set<TransactionDetails>,
    @JsonProperty("pages") val pages: Set<TransactionHistoryPageMetadata>,
    @JsonProperty("details") val details: StatementDetails
)