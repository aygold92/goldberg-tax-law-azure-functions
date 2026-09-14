package com.goldberg.law.agent.model.output

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.SerializationFeature
import com.fasterxml.jackson.databind.json.JsonMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule
import com.fasterxml.jackson.module.kotlin.KotlinFeature
import com.fasterxml.jackson.module.kotlin.KotlinModule

/** An agent's final message: its schema's output, or one of the two alternate shapes every schema allows. */
sealed interface AgentResult<out T> {
    data class Output<T>(val value: T) : AgentResult<T>

    /** `{"file": …}` — the output was too large to return inline and was written to `/mnt/session/outputs/`. */
    data class OutputFile(val file: String) : AgentResult<Nothing>

    /** `{"error": …}` — the agent finished but found nothing it could do (e.g. no statement in the page range). */
    data class Error(val error: String) : AgentResult<Nothing>
}

object AgentOutputParser {
    /**
     * Separate from `OBJECT_MAPPER` because agent JSON needs different handling: an explicit `null` must fall
     * back to a Kotlin default (an agent may write `"check_pages": null` for an omitted list), and dates must
     * serialize as ISO strings rather than `[2024, 1, 31]` when echoed back through the API.
     */
    val JSON: ObjectMapper = JsonMapper.builder()
        .addModule(KotlinModule.Builder().enable(KotlinFeature.NullIsSameAsDefault).build())
        .addModule(JavaTimeModule())
        .enable(DeserializationFeature.USE_BIG_DECIMAL_FOR_FLOATS)
        .disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
        .build()

    fun <T> parse(text: String, type: Class<T>): AgentResult<T> {
        val node = JSON.readTree(text)
        require(node is ObjectNode) { "Agent output is not a JSON object" }

        if (node.size() == 1) {
            node.get("file")?.takeIf { it.isTextual }?.let { return AgentResult.OutputFile(it.asText()) }
            node.get("error")?.takeIf { it.isTextual }?.let { return AgentResult.Error(it.asText()) }
        }
        // Bind from the text rather than the tree: a tree normalizes BigDecimals (1250.00 → 1.25E+3).
        return AgentResult.Output(JSON.readValue(text, type))
    }
}
