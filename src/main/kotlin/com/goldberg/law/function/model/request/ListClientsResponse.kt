package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class ListClientsResponse @Inject constructor(
    @JsonProperty("clients") val clients: Set<String>,
)