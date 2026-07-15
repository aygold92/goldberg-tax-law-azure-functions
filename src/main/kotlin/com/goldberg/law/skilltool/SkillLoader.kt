package com.goldberg.law.skilltool

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Discovers and validates skills under a managed-agents root.
 *
 * Expected layout: one directory per agent, each containing a `skill/` subdirectory with a
 * `SKILL.md`. Linting throws on the first problem so a bad skill never publishes half a batch.
 */
class SkillLoader(private val agentsRoot: Path) {

    init {
        require(agentsRoot.isDirectory()) { "agents root does not exist: $agentsRoot" }
    }

    /** Loads every agent's skill, or only those named in [only]. */
    fun loadBundles(only: List<String> = emptyList()): List<SkillBundle> {
        val agentDirs = agentsRoot.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("skill/SKILL.md").exists() }
            .sortedBy { it.name }

        val selected = if (only.isEmpty()) agentDirs
        else {
            val byName = agentDirs.associateBy { it.name }
            only.map { byName[it] ?: error("no agent named '$it' under $agentsRoot") }
        }
        return selected.map { loadBundle(it) }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun loadBundle(agentDir: Path): SkillBundle {
        val skillDir = agentDir.resolve("skill")
        val skillMd = skillDir.resolve("SKILL.md")
        require(skillMd.isRegularFile()) { "'${agentDir.name}': missing skill/SKILL.md" }

        // Guard against a typo silently creating a *new* skill instead of a new version: the SKILL.md
        // frontmatter `name` is the skill's identity, and must match the agent dir name we publish as.
        val declaredName = frontmatterName(skillMd.readText())
        require(declaredName == agentDir.name) {
            "'${agentDir.name}': SKILL.md frontmatter name '${declaredName ?: "(missing)"}' must match the agent directory name"
        }

        // All real files under skill/, skipping hidden/junk entries (.DS_Store, editor swap files).
        val files = skillDir.walk()
            .filter { it.isRegularFile() && it.relativeTo(skillDir).none { part -> part.name.startsWith(".") } }
            .sorted()
            .toList()
        require(files.isNotEmpty()) { "'${agentDir.name}': no files to upload under skill/" }

        return SkillBundle(agentDir.name, skillDir, files)
    }

    /** Reads the `name:` field from the leading `--- … ---` YAML frontmatter block, or null if absent. */
    private fun frontmatterName(text: String): String? {
        val lines = text.lines()
        if (lines.firstOrNull()?.trim() != "---") return null
        val end = lines.drop(1).indexOfFirst { it.trim() == "---" }
        if (end < 0) return null
        return lines.subList(1, end + 1).firstNotNullOfOrNull { line ->
            val i = line.indexOf(':')
            if (i > 0 && line.substring(0, i).trim() == "name") line.substring(i + 1).trim().ifEmpty { null } else null
        }
    }
}
