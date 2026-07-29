package com.goldberg.law.managedagents

/**
 * The Anthropic resource types this tool manages, one top-level directory each under
 * `managed-agents/`. The API has no ownership hierarchy — every type is independent and refers to
 * the others by id — so the on-disk layout mirrors that and nothing is nested.
 */
enum class ResourceType(
    /** How the type is spelled inside a `{resource: <refName>, name: …}` construct. */
    val refName: String,
    /** Directory under the managed-agents root holding definitions of this type. */
    val dirName: String,
    val layout: Layout,
) {
    SKILL("skill", "skills", Layout.BUNDLE),
    MEMORY_STORE("memory-store", "memory-stores", Layout.FILE),
    ENVIRONMENT("environment", "environments", Layout.FILE),
    AGENT("agent", "agents", Layout.DIRECTORY),
    DEPLOYMENT("deployment", "deployments", Layout.FILE);

    enum class Layout {
        /** `<dirName>/<name>.yaml` */
        FILE,

        /** `<dirName>/<name>/agent.yaml`, alongside the prompt files it references. */
        DIRECTORY,

        /** `<dirName>/<name>/SKILL.md` plus references; uploaded as files, not a JSON body. */
        BUNDLE,
    }

    /** Types loaded from a YAML config file — everything except skills, which are file bundles. */
    val hasYamlConfig: Boolean get() = layout != Layout.BUNDLE

    companion object {
        /** The order stages are applied in: a type never references one that comes after it. */
        val APPLY_ORDER = listOf(SKILL, MEMORY_STORE, ENVIRONMENT, AGENT, DEPLOYMENT)

        fun fromRefName(name: String) = entries.firstOrNull { it.refName == name }
            ?: error("Unknown resource type '$name'. Ref must match one of: ${entries.map { it.refName }}")
    }
}
