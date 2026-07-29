package com.goldberg.law.managedagents.skill

import java.nio.file.Path
import kotlin.io.path.relativeTo

/**
 * A validated skill ready to publish.
 *
 * [name] is the skill's directory name under `managed-agents/skills/` (which [SkillLoader] verifies
 * matches the SKILL.md frontmatter `name`). It's sent as the Anthropic `display_title` and used to
 * find an existing skill, so re-publishing the same name appends a version rather than creating a
 * duplicate. It is also what `{resource: skill, name: …}` in an agent config refers to.
 *
 * Skills are independent of agents — nothing stops two agents pointing at the same one.
 *
 * [filePaths] are all upload files under [rootDir]; their paths relative to [rootDir] (e.g. `SKILL.md`,
 * `references/output-schema.md`) become the uploaded filenames so the bundle structure is preserved.
 */
data class SkillBundle(
    val name: String,
    val rootDir: Path,
    val filePaths: List<Path>,
) {
    /**
     * Upload path to source file, for each file. The API requires all files under a single top-level
     * folder whose name matches the SKILL.md `name`, so each path is prefixed with `<name>/`
     * (e.g. `bank-statement-extraction/SKILL.md`).
     */
    fun uploadFiles(): List<Pair<String, Path>> = filePaths.map { file ->
        "$name/${file.relativeTo(rootDir).joinToString("/")}" to file
    }
}
