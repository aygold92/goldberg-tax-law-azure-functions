package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.node.ObjectNode
import com.goldberg.law.managedagents.skill.SkillLoader
import java.nio.file.Path
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.nameWithoutExtension
import kotlin.io.path.readText

/**
 * Loads and lints every resource under `managed-agents/`.
 *
 * Everything is read and validated before the caller publishes anything, so a typo fails the run
 * rather than leaving half the workspace updated.
 */
class ResourceConfigLoader(private val root: Path) {

    init {
        require(root.isDirectory()) { "managed-agents root does not exist: $root" }
    }

    fun load(): ManagedAgentsResourceConfig {
        val skills = SkillLoader(root.resolve(ResourceType.SKILL.dirName)).loadBundles()
        val specs = ResourceType.entries
            .filter { it.hasYamlConfig }
            .associateWith { loadSpecs(it) }

        val config = ManagedAgentsResourceConfig(skills, specs)
        lintReferences(config)
        lintMemoryMounts(config)
        return config
    }

    private fun loadSpecs(type: ResourceType): List<ResourceSpec> {
        val dir = root.resolve(type.dirName)
        if (!dir.isDirectory()) return emptyList()

        val sources = when (type.layout) {
            ResourceType.Layout.FILE ->
                dir.listDirectoryEntries()
                    .filter { it.isRegularFile() && it.isYaml() && !it.name.startsWith(".") }

            ResourceType.Layout.DIRECTORY ->
                dir.listDirectoryEntries()
                    .filter { it.isDirectory() && !it.name.startsWith(".") }
                    .map { it.resolve(AGENT_CONFIG_FILE) }
                    .filter { it.isRegularFile() }

            ResourceType.Layout.BUNDLE -> error("${type.dirName} is not YAML-configured")
        }

        return sources.sortedBy { expectedName(type, it) }
            .map { loadSpec(type, it) }
    }

    private fun loadSpec(type: ResourceType, source: Path): ResourceSpec {
        val parsed = Yaml.read(source)
        val declaredName = Yaml.nameOf(parsed)
        val expectedName = expectedName(type, source)

        // The name is the resource's identity: it is how an existing resource is found for update and
        // how `{resource: …}` refers to it. A mismatch would quietly create a second resource.
        require(declaredName == expectedName) {
            "${pathFromRoot(source)}: name '${declaredName ?: "(missing)"}' must match " +
                if (type.layout == ResourceType.Layout.DIRECTORY) "the directory name '$expectedName'"
                else "the file name '$expectedName'"
        }

        val resolved = DeferredValues.resolveFiles(parsed, source.parent, root) as ObjectNode
        return ResourceSpec(
            type = type,
            name = expectedName,
            source = source,
            body = resolved,
            references = DeferredValues.collectReferences(resolved),
        )
    }

    /** Every `{resource: …}` must point at something that actually exists on disk. */
    private fun lintReferences(config: ManagedAgentsResourceConfig) {
        config.resourceSpecs.values.flatten().forEach { spec ->
            spec.references.forEach { ref ->
                require(config.defines(ref)) {
                    "${pathFromRoot(spec.source)}: $ref does not match any " +
                        "${ref.type.dirName}/ definition"
                }
            }
        }
    }

    /**
     * A memory store is mounted at a path derived from its name, and the skills and prompts address
     * those paths as literal strings — nothing links the two but the name. So every `/mnt/memory/x/`
     * mentioned anywhere must correspond to a defined store, or the agent will read an empty mount
     * and silently behave as if it had no memory.
     */
    private fun lintMemoryMounts(config: ManagedAgentsResourceConfig) {
        val defined = config.specsOf(ResourceType.MEMORY_STORE).map { slugify(it.name) }.toSet()

        val sources = buildList {
            config.skills.forEach { skill -> skill.filePaths.forEach { add(it to it.readText()) } }
            config.specsOf(ResourceType.AGENT).forEach { add(it.source to it.body.toString()) }
        }

        sources.forEach { (path, text) ->
            MOUNT_PATTERN.findAll(text).map { it.groupValues[1] }.distinct().forEach { slug ->
                require(slugify(slug) in defined) {
                    "${root.relativize(path)}: references /mnt/memory/$slug/ but no memory store is " +
                        "defined with that name (have: ${defined.sorted().joinToString()})"
                }
            }
        }
    }

    private fun expectedName(type: ResourceType, source: Path): String =
        if (type.layout == ResourceType.Layout.DIRECTORY) source.parent.name else source.nameWithoutExtension

    private fun pathFromRoot(source: Path): String = root.relativize(source).toString()

    private fun Path.isYaml() = name.endsWith(".yaml") || name.endsWith(".yml")

    /** Mirrors the API's sanitisation of a store name into its mount-path segment. */
    private fun slugify(value: String) = value.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-')

    companion object {
        const val AGENT_CONFIG_FILE = "agent.yaml"
        private val MOUNT_PATTERN = Regex("""/mnt/memory/([A-Za-z0-9._-]+)""")
    }
}
