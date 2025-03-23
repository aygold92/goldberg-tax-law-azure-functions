package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class PutDocumentDataModelResponse @Inject constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("value") val value: String
)