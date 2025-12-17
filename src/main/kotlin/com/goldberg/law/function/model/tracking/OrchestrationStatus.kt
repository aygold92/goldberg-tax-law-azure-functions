package com.goldberg.law.function.model.tracking

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.microsoft.durabletask.TaskOrchestrationContext
import java.util.*

data class OrchestrationStatus(
    @JsonIgnore @Transient val ctx: TaskOrchestrationContext,
    private var stage: OrchestrationStage,
    private val docs: MutableMap<UUID, DocumentOrchestrationStatus>,
) {
    @get:JsonProperty("totalDocuments")
    private val totalDocs: Int?
        get() = this@OrchestrationStatus.docs.map { it.value.numDocsTotal }.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

    @get:JsonProperty("docsCompleted")
    private val docsCompleted: Int?
        get() = this@OrchestrationStatus.docs.map { it.value.docsAnalyzed }.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

    fun updateStage(stage: OrchestrationStage): OrchestrationStatus {
        this.stage = stage
        return this
    }

    fun updateDoc(fileId: UUID, updater: DocumentOrchestrationStatus.() -> Unit): OrchestrationStatus {
        this.docs[fileId]?.updater()
        return this
    }

    fun save() = this.also {
        ctx.setCustomStatus(getExternalStatus())
    }

    // for unit testing
    fun getExternalStatus() = ExternalOrchestrationStatus(
        stage, this@OrchestrationStatus.docs, docsCompleted, totalDocs
    )

    data class ExternalOrchestrationStatus(
        val stage: OrchestrationStage,
        val docs: Map<UUID, DocumentOrchestrationStatus>,
        val docsCompleted: Int?,
        val totalDocs: Int?,
    )
}