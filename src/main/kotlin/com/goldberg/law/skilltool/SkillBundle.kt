package com.goldberg.law.skilltool

import java.nio.file.Path

/**
 * A validated skill ready to publish.
 *
 * [name] is the agent directory name (which [SkillLoader] verifies matches the SKILL.md frontmatter
 * `name`). It's sent as the Anthropic `display_title` and used to find an existing skill, so
 * re-publishing the same name appends a version rather than creating a duplicate.
 *
 * [filePaths] are all upload files under [rootDir]; their paths relative to [rootDir] (e.g. `SKILL.md`,
 * `references/output-schema.md`) become the uploaded filenames so the bundle structure is preserved.
 */
data class SkillBundle(
    val name: String,
    val rootDir: Path,
    val filePaths: List<Path>,
)
