package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class FetchDocumentMetadataResponse @JsonCreator constructor(
    @JsonProperty("classified") val classified: Boolean,
    @JsonProperty("numStatements") val numStatements: Int,
    @JsonProperty("statements") val statements: Set<String>,
) 