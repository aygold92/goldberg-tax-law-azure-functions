package com.goldberg.law.function.model.activity

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class ProcessStatementsActivityInput @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("documentDataModels") val documentDataModels: Set<DocumentDataModelContainer>,
    @JsonProperty("metadataMap") val metadataMap: Map<String, InputFileMetadata>
)