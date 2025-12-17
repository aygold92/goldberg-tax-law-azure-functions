package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class GetInputFileSummaryRequest @JsonCreator constructor(
    @JsonProperty("fileId") val fileId: UUID,
)
