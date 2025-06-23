package com.goldberg.law.function.model.tracking

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata

data class DocumentOrchestrationStatus @JsonCreator constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("numStatements") var numStatements: Int?,
    @JsonProperty("statementsCompleted") var statementsCompleted: Int?,
    @JsonProperty("classified") var classified: Boolean?,
) {
    fun incrementStatementsCompleted() = this.also { statementsCompleted = statementsCompleted?.plus(1) }
}