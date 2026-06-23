package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.*

data class DeleteCheckRequest @JsonCreator constructor(
    @JsonProperty("checkId") val checkId: UUID,
)
