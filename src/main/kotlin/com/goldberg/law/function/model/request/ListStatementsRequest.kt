package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class ListStatementsRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String
) 