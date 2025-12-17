package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.util.UUID

data class ListStatementsRequest @JsonCreator constructor(
    @JsonProperty("clientId") val clientId: UUID
) 