package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import java.nio.file.Path
import kotlin.io.path.readText

/**
 * YAML parsing for the config files.
 *
 * The API itself is JSON-only — the Console's YAML view is a UI-side representation — but we author
 * YAML in the repo for comments and readable diffs, and serialize on the way out. Because the SDK's
 * `*Params.Body` classes are Jackson-deserializable and capture unknown keys via `@JsonAnySetter`,
 * a parsed config can be handed to the SDK wholesale: no field-by-field mapping, and a field the API
 * adds later works with no change here.
 */
object Yaml {

    private val mapper = ObjectMapper(YAMLFactory())

    /** Parses a config file, which must be a YAML mapping at the top level. */
    fun read(path: Path): ObjectNode {
        val parsed = runCatching { mapper.readTree(path.readText()) }
            .getOrElse { error("${path.fileName}: could not parse YAML — ${it.message}") }
        require(parsed is ObjectNode) {
            "${path.fileName}: expected a YAML mapping at the top level, got ${parsed?.nodeType ?: "nothing"}"
        }
        return parsed
    }

    /** Reads the `name:` field a config identifies itself by. */
    fun nameOf(node: JsonNode): String? = node.get("name")?.takeIf { it.isTextual }?.asText()
}
