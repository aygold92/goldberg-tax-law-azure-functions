package com.goldberg.law.function.activity.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.entity.Classification
import com.goldberg.law.function.api.model.ClassificationProcessingOptions

data class ProcessDataModelActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("classification") val classification: Classification,
    @JsonProperty("processingOptions") val processingOptions: ClassificationProcessingOptions,
    @JsonProperty("useOriginalFile") val useOriginalFile: Boolean = false,
)