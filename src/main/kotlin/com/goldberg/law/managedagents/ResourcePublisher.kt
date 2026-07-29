package com.goldberg.law.managedagents

import com.anthropic.core.jsonMapper
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.JsonNodeFactory
import com.fasterxml.jackson.databind.node.ObjectNode
import io.github.oshai.kotlinlogging.KotlinLogging

enum class Outcome { CREATED, UPDATED, UNCHANGED }

data class ApplyResult(val id: String, val outcome: Outcome)

/**
 * Create-or-update for the four resource types that are configured with a JSON body.
 *
 * The API has no "get by name" and no upsert, but every type has a paginated `list()` that returns a
 * `name`, so identity is resolved the same way [skill.SkillPublisher] resolves it: list, match by
 * name, then create or update. That is what keeps the tool stateless — there is no id lockfile to
 * commit or race on in CI.
 *
 * A `config_sha256` of the body is stamped into the resource's `metadata`. Because `list()` already
 * returns metadata, comparing it costs no extra request, so an unchanged resource is skipped for free.
 *
 * Subclasses receive the fully resolved config as a [JsonNode] and convert it with
 * `jsonMapper().convertValue(node, …CreateParams.Body::class.java)`. The SDK's body classes are
 * Jackson-deserializable and capture unknown keys via `@JsonAnySetter`, so the config passes straight
 * through: no field-by-field mapping, and a field the API adds later needs no change here.
 */
abstract class ResourcePublisher<R>(val type: ResourceType) {

    // Named from the runtime class, so each subclass logs under its own name. `KotlinLogging.logger {}`
    // here would resolve to this base class, making every publisher's output indistinguishable.
    protected val logger = KotlinLogging.logger(javaClass.name)

    protected abstract fun create(body: JsonNode): R
    protected abstract fun update(existing: R, body: JsonNode): R
    protected abstract fun list(): Sequence<R>
    protected abstract fun nameOf(remote: R): String
    protected abstract fun idOf(remote: R): String
    protected abstract fun configHashOf(remote: R): String?

    /** Finds a resource by name, for `--resource-types` runs where this stage's ids were never registered. */
    fun lookupId(name: String): String? = list().firstOrNull { nameOf(it) == name }?.let(::idOf)

    /**
     * Reads our hash back out of a resource's metadata. Each resource type has its own `Metadata`
     * class, but all of them serialize their arbitrary keys the same way
     */
    protected fun hashFromMetadata(metadata: Any?): String? = metadata
        ?.let { jsonMapper().valueToTree<JsonNode>(it) }
        ?.get(ContentHash.METADATA_KEY)
        ?.takeIf { it.isTextual }
        ?.asText()

    fun apply(spec: ResourceSpec, registry: ResourceRegistry): ApplyResult {
        val resolvedConfig = DeferredValues.resolveReferences(spec.body, registry) as ObjectNode
        val hash = ContentHash.ofConfig(resolvedConfig)
        val existing = list().firstOrNull { nameOf(it) == spec.name }

        if (existing != null && configHashOf(existing) == hash) {
            logger.info { "✓ ${spec.name} ${type.refName} unchanged — skipped" }
            return ApplyResult(idOf(existing), Outcome.UNCHANGED)
        }

        val body = resolvedConfig.withConfigHash(hash)
        val result = if (existing == null) {
            logger.info { "Creating ${type.refName} '${spec.name}'…" }
            ApplyResult(idOf(create(body)), Outcome.CREATED)
        } else {
            logger.info { "Updating ${type.refName} '${spec.name}' (${idOf(existing)})…" }
            ApplyResult(idOf(update(existing, body)), Outcome.UPDATED)
        }

        logger.info { "✓ ${spec.name} → ${type.refName} id=${result.id}" }
        return result
    }

    /** Stamps the hash into `metadata`, preserving whatever metadata the config already declared. */
    private fun ObjectNode.withConfigHash(hash: String): ObjectNode {
        val metadata = (get("metadata") as? ObjectNode)?.deepCopy() ?: JsonNodeFactory.instance.objectNode()
        metadata.put(ContentHash.METADATA_KEY, hash)
        return deepCopy().also { it.set<JsonNode>("metadata", metadata) }
    }
}
