package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.CheckDetails

data class EditChecksRequest @JsonCreator constructor(
    @JsonProperty("updates") val updates: List<CheckDetails>,
)
