package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.node.ObjectNode
import java.nio.file.Path

/**
 * One YAML-configured resource, loaded and linted but not yet published.
 *
 * [body] has had its `{file: …}` constructs resolved already (those are purely local) but still holds
 * its `{resource: …}` constructs, which cannot be resolved until the referenced resources have ids.
 * [references] is what those constructs point at, extracted up front so every reference can be
 * validated — and the `--agents` dependency graph built — before any network call.
 */
data class ResourceSpec(
    val type: ResourceType,
    val name: String,
    val source: Path,
    val body: ObjectNode,
    val references: List<ResourceRef>,
)
