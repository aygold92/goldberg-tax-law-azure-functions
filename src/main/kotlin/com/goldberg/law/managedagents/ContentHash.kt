package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path
import java.security.MessageDigest
import kotlin.io.path.readBytes

/**
 * Content hashing, so that re-running the tool with nothing changed publishes nothing.
 *
 * For agents, environments, memory stores and deployments the hash rides along in the resource's
 * `metadata`, which the `list()` call already returns — so detecting a no-op costs no extra request.
 * Skills have no metadata field and expose no content hash, so their bundle hash is compared against
 * the downloaded latest version instead.
 */
object ContentHash {

    /** Metadata key the config hash is stored under. Well inside the API's 64-char key limit. */
    const val METADATA_KEY = "config_sha256"

    /** Hash of a config body, stable across key ordering. */
    fun ofConfig(node: JsonNode): String = sha256 { it.update(canonicalize(node).toString().toByteArray()) }

    /**
     * Hash of a skill bundle: every file's upload path and bytes, ordered by path so the result does
     * not depend on directory traversal order or platform path separators.
     */
    fun ofFiles(files: List<Pair<String, ByteArray>>): String = sha256 { digest ->
        files.sortedBy { it.first }.forEach { (path, bytes) ->
            digest.update(path.toByteArray())
            digest.update(0)
            digest.update(bytes)
            digest.update(0)
        }
    }

    fun ofPaths(files: List<Pair<String, Path>>): String =
        ofFiles(files.map { (path, file) -> path to file.readBytes() })

    /** Recursively sorts object keys so that reordering YAML fields is not treated as a change. */
    private fun canonicalize(node: JsonNode): JsonNode = when (node) {
        is ObjectNode -> JsonNodeFactory.instance.objectNode().also { out ->
            node.fieldNames().asSequence().sorted().forEach { out.set<JsonNode>(it, canonicalize(node.get(it))) }
        }

        is ArrayNode -> JsonNodeFactory.instance.arrayNode().also { out ->
            node.forEach { out.add(canonicalize(it)) }
        }

        else -> node
    }

    private fun sha256(fill: (MessageDigest) -> Unit): String =
        MessageDigest.getInstance("SHA-256").apply(fill).digest().joinToString("") { "%02x".format(it) }
}
