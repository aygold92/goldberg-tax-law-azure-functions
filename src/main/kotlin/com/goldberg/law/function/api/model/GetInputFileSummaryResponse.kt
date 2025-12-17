package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.InputFileSummary

data class GetInputFileSummaryResponse @JsonCreator constructor(
    @JsonProperty("summary") val summary: InputFileSummary,
)
