package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.DocumentDataModel
import javax.inject.Inject

data class PutDocumentDataModelResponse @Inject constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("model") val model: DocumentDataModel
)