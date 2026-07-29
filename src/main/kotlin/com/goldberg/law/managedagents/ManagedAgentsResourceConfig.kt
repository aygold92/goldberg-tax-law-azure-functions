package com.goldberg.law.managedagents

import com.goldberg.law.managedagents.skill.SkillBundle

/**
 * Everything under `managed-agents/`, loaded and linted.
 *
 * Skills are file bundles rather than JSON bodies, so they are held separately from the YAML-backed
 * [resourceSpecs]; both are addressed by name through the same [ResourceRef] vocabulary.
 */
data class ManagedAgentsResourceConfig(
    val skills: List<SkillBundle>,
    val resourceSpecs: Map<ResourceType, List<ResourceSpec>>,
) {
    fun specsOf(type: ResourceType): List<ResourceSpec> = resourceSpecs[type].orEmpty()

    fun isEmpty(): Boolean = skills.isEmpty() && resourceSpecs.values.all { it.isEmpty() }

    /** True if something with this name is defined on disk — the check every reference must pass. */
    fun defines(ref: ResourceRef): Boolean = when (ref.type) {
        ResourceType.SKILL -> skills.any { it.name == ref.name }
        else -> specsOf(ref.type).any { it.name == ref.name }
    }

    /**
     * Narrows to the named agents and everything they depend on: the skills their configs reference,
     * any deployment that targets them, and in turn whatever those deployments reference.
     *
     * Filtering by the reference graph rather than by directory is what makes shared resources work —
     * a skill used by two agents is selected by either, which a layout-based filter could not do.
     */
    fun filterToSpecifiedAgents(agentNames: List<String>): ManagedAgentsResourceConfig {
        if (agentNames.isEmpty()) return this

        val agentsByName = specsOf(ResourceType.AGENT).associateBy { it.name }
        val seeds = agentNames.map { agentsByName[it] ?: error("no agent named '$it'") }

        val selectedSkills = mutableSetOf<String>()
        val selectedSpecs = mutableSetOf<ResourceRef>()
        val queue = ArrayDeque<ResourceSpec>()

        fun select(spec: ResourceSpec) {
            if (selectedSpecs.add(ResourceRef(spec.type, spec.name))) queue += spec
        }

        seeds.forEach(::select)
        specsOf(ResourceType.DEPLOYMENT)
            .filter { deployment ->
                deployment.references.any { it.type == ResourceType.AGENT && it.name in agentNames }
            }
            .forEach(::select)

        while (queue.isNotEmpty()) {
            queue.removeFirst().references.forEach { ref ->
                if (ref.type == ResourceType.SKILL) {
                    selectedSkills += ref.name
                } else {
                    specsOf(ref.type).firstOrNull { it.name == ref.name }?.let(::select)
                }
            }
        }

        return ManagedAgentsResourceConfig(
            skills = skills.filter { it.name in selectedSkills },
            resourceSpecs = resourceSpecs.mapValues { (type, list) ->
                list.filter { ResourceRef(type, it.name) in selectedSpecs }
            },
        )
    }
}
