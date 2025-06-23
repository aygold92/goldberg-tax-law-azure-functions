package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.DocumentDataModelContainer
import javax.inject.Inject

class PutDocumentDataModelRequest @Inject constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("model") val model: DocumentDataModelContainer
)