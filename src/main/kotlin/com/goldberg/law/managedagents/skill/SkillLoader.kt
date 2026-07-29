package com.goldberg.law.managedagents.skill

import java.nio.file.Path
import kotlin.io.path.ExperimentalPathApi
import kotlin.io.path.exists
import kotlin.io.path.isDirectory
import kotlin.io.path.isRegularFile
import kotlin.io.path.isSymbolicLink
import kotlin.io.path.listDirectoryEntries
import kotlin.io.path.name
import kotlin.io.path.readText
import kotlin.io.path.relativeTo
import kotlin.io.path.walk

/**
 * Discovers and validates skills under `managed-agents/skills/`.
 *
 * Expected layout: one directory per skill, each containing a `SKILL.md`. Linting throws on the first
 * problem so a bad skill never publishes half a batch.
 *
 * A published bundle has to be self-contained — the API has no cross-skill file sharing — so markdown
 * two skills share lives in `shared/skills/` and is symlinked into each skill's `references/`. Nothing
 * here special-cases that: a symlinked file is walked, hashed and uploaded like any other.
 */
class SkillLoader(private val skillsRoot: Path) {

    /** Loads every skill, or only those named in [only]. An absent `skills/` directory means none. */
    fun loadBundles(only: List<String> = emptyList()): List<SkillBundle> {
        if (!skillsRoot.isDirectory()) {
            require(only.isEmpty()) { "no skills directory at $skillsRoot, but skills were requested: $only" }
            return emptyList()
        }

        val skillDirs = skillsRoot.listDirectoryEntries()
            .filter { it.isDirectory() && it.resolve("SKILL.md").exists() }
            .sortedBy { it.name }

        val selected = if (only.isEmpty()) skillDirs
        else {
            val byName = skillDirs.associateBy { it.name }
            only.map { byName[it] ?: error("no skill named '$it' under $skillsRoot") }
        }
        return selected.map { loadBundle(it) }
    }

    @OptIn(ExperimentalPathApi::class)
    private fun loadBundle(skillDir: Path): SkillBundle {
        val skillMd = skillDir.resolve("SKILL.md")
        require(skillMd.isRegularFile()) { "'${skillDir.name}': missing SKILL.md" }

        // Guard against a typo silently creating a *new* skill instead of a new version: the SKILL.md
        // frontmatter `name` is the skill's identity, and must match the directory we publish as.
        val declaredName = frontmatterName(skillMd.readText())
        require(declaredName == skillDir.name) {
            "'${skillDir.name}': SKILL.md frontmatter name '${declaredName ?: "(missing)"}' " +
                "must match the skill directory name"
        }

        // Everything under the skill dir, skipping hidden/junk entries (.DS_Store, swap files).
        val entries = skillDir.walk()
            .filter { it.relativeTo(skillDir).none { part -> part.name.startsWith(".") } }
            .sorted()
            .toList()

        // Shared markdown is symlinked in from shared/skills/ and published as an ordinary bundle file
        // (isRegularFile and readBytes both follow links). One that doesn't resolve would otherwise be
        // dropped by the filter below, silently publishing a bundle with a file missing.
        val unresolved = entries.filter { it.isSymbolicLink() && !it.isRegularFile() }
        require(unresolved.isEmpty()) {
            "'${skillDir.name}': symlink(s) ${unresolved.joinToString { it.relativeTo(skillDir).toString() }} " +
                "do not resolve to a regular file (a broken link, or a link to a directory — " +
                "link individual files, since the walk does not descend symlinked directories)"
        }

        val files = entries.filter { it.isRegularFile() }
        require(files.isNotEmpty()) { "'${skillDir.name}': no files to upload" }

        return SkillBundle(skillDir.name, skillDir, files)
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
