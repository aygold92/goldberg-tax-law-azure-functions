package com.goldberg.law.managedagents

import com.goldberg.law.managedagents.skill.SkillPublisher

/** A `{resource: <type>, name: <name>}` reference found in a config body. */
data class ResourceRef(val type: ResourceType, val name: String) {
    override fun toString() = "{resource: ${type.refName}, name: $name}"
}

/**
 * Maps a resource's repo-local name to the id the API assigned it, so that later stages can resolve
 * the `{resource: …}` constructs in their bodies.
 *
 * Stages populate this as they publish. When a stage is skipped (`--resource-types`), its ids are not known
 * locally, so resolution falls back to [remoteLookup] — a `list()` against the API, matched by name.
 * Remote lookups are cached, since several bodies typically reference the same resource.
 */
class ResourceRegistry(
    private val publishers: Map<ResourceType, ResourcePublisher<out Any>>,
    private val skillPublisher: SkillPublisher,
) {
    private val ids = mutableMapOf<ResourceRef, String>()

    fun register(type: ResourceType, name: String, id: String) {
        ids[ResourceRef(type, name)] = id
    }

    /** Resolves a reference to an id, consulting the API only if the resource has not already been processed */
    fun resolve(ref: ResourceRef): String = ids.getOrPut(ref) {
        fetchRemote(ref.type, ref.name) ?: error(
            "could not resolve $ref — no such ${ref.type.refName} was published in this run or found in the workspace"
        )
    }

    private fun fetchRemote(type: ResourceType, name: String): String? = (
        if (type == ResourceType.SKILL) skillPublisher.retrieve(name)?.id()
        else publishers[type]?.lookupId(name)
    )
}
