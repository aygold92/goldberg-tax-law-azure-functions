package com.goldberg.law.skilltool

import com.anthropic.client.AnthropicClient
import com.anthropic.core.MultipartField
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.skills.SkillCreateParams
import com.anthropic.models.beta.skills.SkillListParams
import com.anthropic.models.beta.skills.versions.VersionCreateParams
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo

/**
 * Publishes skills to the Anthropic Skills API. Idempotent by skill name: if a skill with the same
 * `display_title` already exists it appends a new version; otherwise it creates the skill.
 */
class SkillPublisher(
    private val client: AnthropicClient,
) {
    private val logger = KotlinLogging.logger {}
    private val beta = AnthropicBeta.SKILLS_2025_10_02

    fun publish(skill: SkillBundle) {
        val skills = client.beta().skills()
        val existing = skills.list(SkillListParams.builder().addBeta(beta).build())
            .autoPager()
            .firstOrNull { it.displayTitle().orElse(null) == skill.name }

        val (skillId, version) = if (existing == null) {
            logger.info { "Creating new skill '${skill.name}' (${skill.filePaths.size} files)…" }
            val resp = skills.create(
                SkillCreateParams.builder()
                    .addBeta(beta)
                    .displayTitle(skill.name)
                    .apply { skill.toMultipartFields().forEach { addFile(it) } }
                    .build()
            )
            resp.id() to resp.latestVersion().orElse("1")
        } else {
            logger.info { "Publishing new version of '${skill.name}' (skill ${existing.id()})…" }
            val resp = skills.versions().create(
                existing.id(),
                VersionCreateParams.builder()
                    .addBeta(beta)
                    .apply { skill.toMultipartFields().forEach { addFile(it) } }
                    .build()
            )
            resp.skillId() to resp.version()
        }

        logger.info { "✓ ${skill.name} → skill_id=$skillId version=$version" }
    }

    /**
     * Fresh multipart fields on each call — the backing input streams are single-use.
     *
     * The API requires all files under a single top-level folder whose name matches the SKILL.md
     * `name`, so each path is prefixed with `<name>/` (e.g. `bank-statement-extraction/SKILL.md`).
     * The loader already guarantees [name] equals the frontmatter `name`.
     */
    private fun SkillBundle.toMultipartFields(): List<MultipartField<InputStream>> = filePaths.map { filePath ->
        val pathFromRoot = filePath.relativeTo(rootDir).joinToString("/")
        val uploadPath = "$name/$pathFromRoot"
        MultipartField.builder<InputStream>()
            .value(filePath.readBytes().inputStream())
            .filename(uploadPath)
            .contentType(if (uploadPath.endsWith(".md")) "text/markdown" else "application/octet-stream")
            .build()
    }
}
