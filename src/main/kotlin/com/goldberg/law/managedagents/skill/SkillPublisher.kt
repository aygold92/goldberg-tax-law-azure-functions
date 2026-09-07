package com.goldberg.law.managedagents.skill

import com.anthropic.client.AnthropicClient
import com.anthropic.core.MultipartField
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.skills.SkillCreateParams
import com.anthropic.models.beta.skills.SkillListParams
import com.anthropic.models.beta.skills.versions.VersionCreateParams
import com.anthropic.models.beta.skills.versions.VersionRetrieveParams
import com.goldberg.law.managedagents.ApplyResult
import com.goldberg.law.managedagents.ContentHash
import com.goldberg.law.managedagents.Outcome
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.zip.ZipInputStream
import kotlin.io.path.readBytes

/**
 * Publishes skills to the Anthropic Skills API. Idempotent by skill name: if a skill with the same
 * `display_title` already exists it appends a new version; otherwise it creates the skill.
 *
 * Unlike the other resource types, skills carry no `metadata` and expose no content hash, so a no-op
 * cannot be detected from the listing alone. Instead the latest version's zip is downloaded and
 * hashed — two extra requests per skill, which is what keeps a re-run from stacking up an identical
 * version every time the tool is invoked.
 */
class SkillPublisher(
    private val client: AnthropicClient,
) {
    private val logger = KotlinLogging.logger {}
    private val beta = AnthropicBeta.SKILLS_2025_10_02
    private val skills get() = client.beta().skills()

    private val http: HttpClient by lazy { HttpClient.newHttpClient() }
    private val baseUrl by lazy {
        (System.getenv("ANTHROPIC_BASE_URL") ?: "https://api.anthropic.com").trimEnd('/')
    }
    private val apiKey by lazy {
        System.getenv("ANTHROPIC_API_KEY") ?: error("ANTHROPIC_API_KEY is not set")
    }

    fun publish(skill: SkillBundle): ApplyResult {
        val localHash = ContentHash.ofPaths(skill.uploadFiles())

        val existing = retrieve(skill.name)

        if (existing != null) {
            val publishedHash = existing.latestVersion().orElse(null)
                ?.let { publishedContentHash(existing.id(), it) }
            if (publishedHash == localHash) {
                logger.info { "✓ ${skill.name} skill unchanged — skipped" }
                return ApplyResult(existing.id(), Outcome.UNCHANGED)
            }
        }

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
        return ApplyResult(skillId, if (existing == null) Outcome.CREATED else Outcome.UPDATED)
    }

    fun retrieve(name: String) = skills.list(SkillListParams.builder().addBeta(beta).build())
        .autoPager()
        .firstOrNull { it.displayTitle().orElse(null) == name }

    /**
     * Hash of what is already published, computed the same way as the local bundle so the two are
     * comparable. A failure here is not fatal — we simply fall through and publish a new version,
     * but it is logged at warn: a silent failure here means every run republishes.
     *
     * The content endpoint is fetched directly rather than through the SDK, because the SDK pins
     * `anthropic-beta: skills-2025-10-02` on every skill-version call and the endpoint rejects that
     * header for a workspace API key. Without the header it addresses a version by its id
     * (`skill_version_…`) rather than the numeric `version`, so the id is looked up first — that
     * call is a normal SDK call and does want the beta header.
     */
    private fun publishedContentHash(skillId: String, version: String): String? = runCatching {
        val versionId = skills.versions().retrieve(
            version,
            VersionRetrieveParams.builder().addBeta(beta).skillId(skillId).build(),
        ).id()

        downloadVersionContent(skillId, versionId).use { body ->
            val entries = buildList {
                ZipInputStream(body).use { zip ->
                    generateSequence { zip.nextEntry }
                        .filterNot { it.isDirectory }
                        .forEach { add(it.name to zip.readBytes()) }
                }
            }
            ContentHash.ofFiles(entries)
        }
    }.getOrElse {
        logger.warn { "Could not read published content of '$skillId' v$version (${it.message}); will republish." }
        null
    }

    /** The published zip for one skill version, deliberately without the skills beta header. */
    private fun downloadVersionContent(skillId: String, versionId: String): InputStream {
        val request = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/v1/skills/$skillId/versions/$versionId/content"))
            .header("x-api-key", apiKey)
            .header("anthropic-version", ANTHROPIC_VERSION)
            .GET()
            .build()

        val response = http.send(request, HttpResponse.BodyHandlers.ofInputStream())
        check(response.statusCode() == 200) {
            "GET ${request.uri()} → ${response.statusCode()}: " +
                response.body().reader().readText().take(500)
        }
        return response.body()
    }

    /**
     * Fresh multipart fields on each call — the backing input streams are single-use.
     *
     * The API requires all files under a single top-level folder whose name matches the SKILL.md
     * `name`; [SkillBundle.uploadFiles] applies that prefix, and the loader already guarantees the
     * bundle name equals the frontmatter `name`.
     */
    private fun SkillBundle.toMultipartFields(): List<MultipartField<InputStream>> =
        uploadFiles().map { (uploadPath, filePath) ->
            MultipartField.builder<InputStream>()
                .value(filePath.readBytes().inputStream())
                .filename(uploadPath)
                .contentType(if (uploadPath.endsWith(".md")) "text/markdown" else "application/octet-stream")
                .build()
        }

    private companion object {
        const val ANTHROPIC_VERSION = "2023-06-01"
    }
}
