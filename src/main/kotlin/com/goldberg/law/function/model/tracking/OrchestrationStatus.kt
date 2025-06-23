package com.goldberg.law.function.model.tracking

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.util.associate
import com.microsoft.durabletask.TaskOrchestrationContext

data class OrchestrationStatus @JsonCreator constructor(
    @JsonIgnore @Transient val ctx: TaskOrchestrationContext,
    @JsonProperty("stage") private var stage: OrchestrationStage,
    @JsonProperty("documents") private val documents: MutableMap<String, DocumentOrchestrationStatus>,
) {
    @get:JsonProperty("totalStatements")
    private val totalStatements: Int?
        get() = documents.map { it.value.numStatements }.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

    @get:JsonProperty("statementsCompleted")
    private val statementsCompleted: Int?
        get() = documents.map { it.value.statementsCompleted }.filterNotNull().takeIf { it.isNotEmpty() }?.sum()

    fun documentMetadataMap() = documents.associate { filename, status ->
        filename to InputFileMetadata(
            numstatements = status.numStatements,
            classified = status.classified ?: false,
            analyzed = docIsComplete(filename),
        )
    }

    fun updateStage(stage: OrchestrationStage): OrchestrationStatus {
        this.stage = stage
        return this
    }

    fun updateDoc(docName: String, updater: DocumentOrchestrationStatus.() -> Unit): OrchestrationStatus {
        this.documents[docName]?.updater()
        return this
    }

    fun getNumStatementsForDoc(docName: String): Int? = documents[docName]?.numStatements

    fun docIsComplete(docName: String): Boolean = documents[docName]?.let {
        it.statementsCompleted != null && it.statementsCompleted == it.numStatements
    } ?: false

    fun save() = this.also {
        ctx.setCustomStatus(getExternalStatus())
    }

    // for unit testing
    fun getExternalStatus() = ExternalOrchestrationStatus(
        stage, documents, statementsCompleted, totalStatements
    )

    data class ExternalOrchestrationStatus(
        @JsonProperty("stage") val stage: OrchestrationStage,
        @JsonProperty("documents") val documents: Map<String, DocumentOrchestrationStatus>,
        @JsonProperty("statementsCompleted") val statementsCompleted: Int?,
        @JsonProperty("totalStatements") val totalStatements: Int?,
    )
}