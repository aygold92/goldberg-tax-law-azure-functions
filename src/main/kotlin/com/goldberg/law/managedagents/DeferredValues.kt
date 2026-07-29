package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path
import kotlin.io.path.isRegularFile
import kotlin.io.path.readText

/**
 * The two constructs that let a YAML config stay byte-for-byte the API's own schema while still
 * pulling in values that aren't known when the file is written. Both work in any config file, at any
 * depth, anywhere a string is expected — they are resolved by a generic walk, not by the loader
 * knowing which fields happen to hold file contents or ids.
 *
 *   `{file: system-prompt.md}`                     -> the file's text, read relative to its own YAML
 *   `{resource: memory-store, name: bank-patterns}` -> the resolved resource id
 *
 * A construct must match its shape *exactly*: one key `file`, or exactly `resource` + `name`. An
 * object that carries a `file` key in any other shape is left untouched, so that a field the API adds
 * later can never be swallowed by this rewriting — the same forward-compatibility that motivates
 * passing configs through unmapped. `resource` is our own vocabulary rather than an API field name,
 * so a malformed one is a typo and is rejected instead of being forwarded to produce a confusing 400.
 *
 * Files resolve at load time; references resolve at publish time, once earlier stages have registered
 * their ids. [collectReferences] bridges the two — it lets the loader validate every reference, and
 * build the dependency graph `--agents` filters on, before a single network call is made.
 */
object DeferredValues {

    private const val FILE_KEY = "file"
    private const val RESOURCE_KEY = "resource"
    private const val NAME_KEY = "name"

    private val nodes: JsonNodeFactory = JsonNodeFactory.instance

    /** Replaces every `{file: …}` with the referenced file's text. */
    fun resolveFiles(node: JsonNode, baseDir: Path, root: Path): JsonNode = walk(node) { obj ->
        if (obj.size() != 1 || !obj.has(FILE_KEY) || !obj.get(FILE_KEY).isTextual) return@walk null
        val relative = obj.get(FILE_KEY).asText()
        val target = baseDir.resolve(relative).normalize()
        require(target.startsWith(root.normalize())) {
            "{file: $relative} escapes the managed-agents root ($root)"
        }
        require(target.isRegularFile()) { "{file: $relative} does not exist (resolved to $target)" }
        nodes.textNode(target.readText().trim())
    }

    /** Every `{resource: …}` in the tree, in encounter order, for linting and dependency filtering. */
    fun collectReferences(node: JsonNode): List<ResourceRef> = buildList {
        walk(node) { obj -> readReference(obj)?.also { add(it) }?.let { nodes.nullNode() } }
    }

    /** Replaces every `{resource: …}` with the id [registry] resolves it to. */
    fun resolveReferences(node: JsonNode, registry: ResourceRegistry): JsonNode = walk(node) { obj ->
        readReference(obj)?.let { nodes.textNode(registry.resolve(it)) }
    }

    /** Parses a reference construct, or returns null if this object isn't one. Throws if malformed. */
    private fun readReference(obj: ObjectNode): ResourceRef? {
        if (!obj.has(RESOURCE_KEY)) return null
        require(
            obj.size() == 2 && obj.get(RESOURCE_KEY).isTextual
                    && obj.has(NAME_KEY) && obj.get(NAME_KEY).isTextual
        ) { "malformed {resource: …} — expected exactly two keys 'resource' and 'name' (both strings), got $obj" }
        return ResourceRef(ResourceType.fromRefName(obj.get(RESOURCE_KEY).asText()), obj.get(NAME_KEY).asText())
    }

    /**
     * Rebuilds the tree, giving [replace] first refusal on every object. Returning a node replaces
     * that subtree and stops the descent; returning null recurses as normal.
     */
    private fun walk(node: JsonNode, replace: (ObjectNode) -> JsonNode?): JsonNode = when (node) {
        is ObjectNode -> replace(node) ?: nodes.objectNode().also { out ->
            node.fieldNames().forEach { key -> out.set<JsonNode>(key, walk(node.get(key), replace)) }
        }

        is ArrayNode -> nodes.arrayNode().also { out -> node.forEach { out.add(walk(it, replace)) } }
        else -> node
    }
}
