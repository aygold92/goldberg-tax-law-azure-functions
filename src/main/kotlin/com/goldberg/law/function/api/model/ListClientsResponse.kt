package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Client
import com.google.inject.Inject

data class ListClientsResponse @Inject constructor(
    @JsonProperty("clients") val clients: List<Client>,
)