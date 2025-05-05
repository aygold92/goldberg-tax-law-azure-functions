package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty

data class DeleteDocumentRequest(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("filename") val filename: String,
)