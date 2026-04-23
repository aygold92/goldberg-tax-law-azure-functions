package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.datamanager.StorageLocation

data class SASTokenResult(
    @JsonProperty("token") val token: String,
    @JsonProperty("storageLocation") val storageLocation: StorageLocation,
)
