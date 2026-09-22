package com.goldberg.law.datamanager

import com.goldberg.law.document.model.input.DocumentDataModel

/**
 * What sits at a classification's [com.goldberg.law.entity.ClassificationInfo.modelLocation]. Both pipelines
 * write to the same blob, so which shape is there follows from which pipeline ran: a classification with an
 * `extractionSessionId` was extracted by an agent, everything else by Azure Document Intelligence.
 */
sealed interface StoredModel {
    /** The Azure pipeline's model, parsed into the shape its [DocumentDataModel] subclasses describe. */
    data class Azure(val model: DocumentDataModel) : StoredModel

    /**
     * An agent's `result.json`, verbatim. Kept as text rather than a parsed
     * [com.goldberg.law.agent.model.output] class so that a run stays readable after its schema moves on.
     */
    data class Agent(val json: String) : StoredModel

    /** For the callers that only work against the Azure pipeline's model, and can't do anything with an agent's. */
    fun asDocumentDataModel(): DocumentDataModel = when (this) {
        is Azure -> model
        is Agent -> throw IllegalStateException(
            "This classification was extracted by an agent; its result is raw JSON, not a DocumentDataModel"
        )
    }
}
