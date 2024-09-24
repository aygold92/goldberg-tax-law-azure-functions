package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.request.AzureAnalyzeDocumentsRequest

data class GetFilesToProcessActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("request") val request: AzureAnalyzeDocumentsRequest
)