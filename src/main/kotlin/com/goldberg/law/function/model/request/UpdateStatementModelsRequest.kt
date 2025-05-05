package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.updatemodel.OverrideModelDetails

data class UpdateStatementModelsRequest(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("stmtFilename") val stmtFilename: String,
    @JsonProperty("modelDetails") val modelDetails: OverrideModelDetails
)