package com.goldberg.law.function.api.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.DocumentDataModel
import com.google.inject.Inject
import java.util.*

data class PutDocumentDataModelResponse @Inject constructor(
    @JsonProperty("classificationId") val classificationId: UUID,
    @JsonProperty("model") val model: DocumentDataModel
)