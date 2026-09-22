package com.goldberg.law.agent

import com.goldberg.law.agent.model.output.CheckExtractionOutput
import com.goldberg.law.agent.model.output.SplitterOutput
import com.goldberg.law.agent.model.output.StatementExtractionOutput

/**
 * The agents defined under `managed-agents/agents/`. [agentName] and [memoryStore] must match the resource
 * names there — names are how [ManagedAgentResourceResolver] finds their ids at runtime, and how a session is
 * traced back to its agent.
 *
 * Memory stores are attached here for session-driven agents because they have no deployment file; the store
 * names mirror the mount paths each agent's skill reads (`/mnt/memory/<name>/`).
 */
enum class ManagedAgent(
    val agentName: String,
    val memoryStore: String?,
    /**
     * The schema class the agent writes to [OUTPUT_FILE]; null for an agent that reports in prose, whose final
     * message is its output.
     */
    val outputType: Class<*>?,
) {
    SPLITTER("bank-statement-splitting", "bank-patterns", SplitterOutput::class.java),
    STATEMENT_EXTRACTION("bank-statement-extraction", "extraction-notes", StatementExtractionOutput::class.java),
    CHECK_EXTRACTION("check-extraction", null, CheckExtractionOutput::class.java),

    /** Deployment-driven: started through a [MemoryConsolidation] deployment, which attaches its store. */
    MEMORY_CONSOLIDATION("memory-consolidation", null, null);

    companion object {
        const val ENVIRONMENT = "pdf-processing"

        /** Where every agent's skill expects to find the PDF bundle. */
        const val BUNDLE_MOUNT_PATH = "/mnt/session/uploads/workspace/bundle.pdf"

        /**
         * The session output file every agent with an [outputType] writes its result to — its schema's output,
         * or the `{"error": …}` shape. It's the whole result: an agent's messages are only logged.
         */
        const val OUTPUT_FILE = "result.json"

        fun byAgentName(name: String): ManagedAgent? = entries.firstOrNull { it.agentName == name }
    }
}

/**
 * The `managed-agents/deployments/` that run [ManagedAgent.MEMORY_CONSOLIDATION] — one per memory store, named
 * for the agent that writes to it. [deploymentName] must match the deployment's filename stem.
 */
enum class MemoryConsolidation(val deploymentName: String) {
    /** Tidies `bank-patterns`, written by the splitter. */
    SPLITTING("memory-consolidation-splitting"),

    /** Tidies `extraction-notes`, written by statement extraction. */
    EXTRACTION("memory-consolidation-extraction"),
}
