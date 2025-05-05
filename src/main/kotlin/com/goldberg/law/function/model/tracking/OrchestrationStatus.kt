package com.goldberg.law.function.model.tracking

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class OrchestrationStatus @JsonCreator constructor(
    @JsonProperty("stage") val stage: OrchestrationStage,
    @JsonProperty("documents") val documents: List<DocumentOrchestrationStatus>,
    @JsonProperty("totalPages") val totalPages: Int?,
    @JsonProperty("pagesCompleted") val pagesCompleted: Int?,
) {
    companion object {
        fun ofAction(stage: OrchestrationStage, filenames: Collection<String>) = OrchestrationStatus(
            stage,
            filenames.map { DocumentOrchestrationStatus(it, null, null, null) },
            null,
            null
        )
    }
}