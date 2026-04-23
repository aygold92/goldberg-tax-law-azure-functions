package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.datamanager.StorageLocation
import javax.inject.Inject

data class FetchReadSASTokenResponse @Inject constructor(
    @JsonProperty("token") val token: String,
    @JsonProperty("storageLocation") val storageLocation: StorageLocation,
)
