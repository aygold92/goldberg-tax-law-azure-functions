package com.goldberg.law.managedagents

import com.goldberg.law.managedagents.skill.SkillBundle
import com.goldberg.law.managedagents.skill.SkillLoader
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.readBytes
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

class SkillLoaderTest {

    @TempDir
    lateinit var skillsRoot: Path

    /** Stands in for `managed-agents/shared/skills/` — a sibling of `skills/`, not inside it. */
    @TempDir
    lateinit var sharedRoot: Path

    /** Creates skills/<skill>/ with a SKILL.md (frontmatter name defaults to the directory name). */
    private fun writeSkill(
        skill: String,
        frontmatterName: String? = skill,
        extraFiles: List<String> = listOf("references/output-schema.md"),
    ) {
        val skillDir = skillsRoot.resolve(skill)
        skillDir.createDirectories()
        val frontmatter = buildString {
            appendLine("---")
            if (frontmatterName != null) appendLine("name: $frontmatterName")
            appendLine("description: a test skill")
            appendLine("---")
            appendLine("# body")
        }
        skillDir.resolve("SKILL.md").writeText(frontmatter)
        extraFiles.forEach { rel ->
            val p = skillDir.resolve(rel)
            p.parent.createDirectories()
            p.writeText("content of $rel")
        }
    }

    /** Writes a file under [sharedRoot], the shared-markdown source a skill links to. */
    private fun sharedFile(name: String, content: String): Path =
        sharedRoot.resolve(name).also { it.writeText(content) }

    /** Symlinks `skills/<skill>/<relative>` at [target], as `ln -s ../../../shared/skills/…` does. */
    private fun link(skill: String, relative: String, target: Path): Path {
        val linkPath = skillsRoot.resolve(skill).resolve(relative)
        linkPath.parent.createDirectories()
        return Files.createSymbolicLink(linkPath, linkPath.parent.relativize(target))
    }

    private fun bundleFiles(bundle: SkillBundle): List<String> =
        bundle.filePaths.map { it.relativeTo(bundle.rootDir).invariantSeparatorsPathString }

    @Test
    fun `loads every skill, sorted by name`() {
        writeSkill("bank-statement-extraction")
        writeSkill("bank-statement-splitting")

        val bundles = SkillLoader(skillsRoot).loadBundles()

        assertThat(bundles.map { it.name })
            .containsExactly("bank-statement-extraction", "bank-statement-splitting")
    }

    @Test
    fun `collects SKILL_md and nested reference files with paths relative to the skill dir`() {
        writeSkill("skill-a", extraFiles = listOf("references/output-schema.md", "references/notes.md"))

        val bundle = SkillLoader(skillsRoot).loadBundles().single()

        assertThat(bundleFiles(bundle))
            .containsExactly("SKILL.md", "references/notes.md", "references/output-schema.md")
    }

    @Test
    fun `skips hidden and junk files`() {
        writeSkill("skill-a")
        val skillDir = skillsRoot.resolve("skill-a")
        skillDir.resolve(".DS_Store").writeText("junk")
        skillDir.resolve("references/.hidden").writeText("junk")

        val bundle = SkillLoader(skillsRoot).loadBundles().single()

        assertThat(bundleFiles(bundle)).noneMatch { it.contains(".DS_Store") || it.contains(".hidden") }
    }

    @Test
    fun `ignores directories without a SKILL_md`() {
        writeSkill("real-skill")
        Files.createDirectories(skillsRoot.resolve("not-a-skill"))

        val bundles = SkillLoader(skillsRoot).loadBundles()

        assertThat(bundles.map { it.name }).containsExactly("real-skill")
    }

    @Test
    fun `filter restricts to the named skills`() {
        writeSkill("skill-a")
        writeSkill("skill-b")

        val bundles = SkillLoader(skillsRoot).loadBundles(only = listOf("skill-b"))

        assertThat(bundles.map { it.name }).containsExactly("skill-b")
    }

    @Test
    fun `unknown skill name in filter throws`() {
        writeSkill("skill-a")

        assertThatThrownBy { SkillLoader(skillsRoot).loadBundles(only = listOf("nope")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no skill named 'nope'")
    }

    @Test
    fun `frontmatter name that differs from the directory name is rejected`() {
        writeSkill("skill-a", frontmatterName = "wrong-name")

        assertThatThrownBy { SkillLoader(skillsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'wrong-name'")
            .hasMessageContaining("must match the skill directory name")
    }

    @Test
    fun `missing frontmatter name is rejected`() {
        writeSkill("skill-a", frontmatterName = null)

        assertThatThrownBy { SkillLoader(skillsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("(missing)")
    }

    @Test
    fun `upload paths are prefixed with the skill name so the bundle has one top-level folder`() {
        writeSkill("skill-a", extraFiles = listOf("references/output-schema.md"))

        val bundle = SkillLoader(skillsRoot).loadBundles().single()

        assertThat(bundle.uploadFiles().map { it.first })
            .containsExactly("skill-a/SKILL.md", "skill-a/references/output-schema.md")
    }

    @Test
    fun `a symlinked shared file is published like any other bundle file`() {
        writeSkill("skill-a")
        val shared = sharedFile("supported-banks-store.md", "/mnt/memory/supported-banks/")
        link("skill-a", "references/supported-banks-store.md", shared)

        val bundle = SkillLoader(skillsRoot).loadBundles().single()

        assertThat(bundle.uploadFiles().map { it.first })
            .contains("skill-a/references/supported-banks-store.md")
        val uploaded = bundle.uploadFiles().single { it.first.endsWith("supported-banks-store.md") }.second
        assertThat(uploaded.readBytes()).isEqualTo("/mnt/memory/supported-banks/".toByteArray())
    }

    @Test
    fun `one shared file linked into two skills lands in both bundles`() {
        writeSkill("skill-a")
        writeSkill("skill-b")
        val shared = sharedFile("shared.md", "shared guidance")
        link("skill-a", "references/shared.md", shared)
        link("skill-b", "references/shared.md", shared)

        val bundles = SkillLoader(skillsRoot).loadBundles()

        assertThat(bundles.map { it.uploadFiles().map { (path, _) -> path } })
            .allSatisfy { paths -> assertThat(paths).anyMatch { it.endsWith("/references/shared.md") } }
    }

    @Test
    fun `editing the shared file changes the bundle hash so both skills republish`() {
        writeSkill("skill-a")
        val shared = sharedFile("shared.md", "first version")
        link("skill-a", "references/shared.md", shared)

        val before = ContentHash.ofPaths(SkillLoader(skillsRoot).loadBundles().single().uploadFiles())
        shared.writeText("second version")
        val after = ContentHash.ofPaths(SkillLoader(skillsRoot).loadBundles().single().uploadFiles())

        assertThat(after).isNotEqualTo(before)
    }

    @Test
    fun `a broken symlink is rejected rather than silently dropped`() {
        writeSkill("skill-a")
        link("skill-a", "references/shared.md", sharedRoot.resolve("does-not-exist.md"))

        assertThatThrownBy { SkillLoader(skillsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("references/shared.md")
            .hasMessageContaining("do not resolve to a regular file")
    }

    @Test
    fun `a symlink to a directory is rejected, since the walk does not descend it`() {
        writeSkill("skill-a")
        val sharedDir = sharedRoot.resolve("bundle").createDirectories()
        sharedDir.resolve("shared.md").writeText("shared guidance")
        link("skill-a", "references/shared", sharedDir)

        assertThatThrownBy { SkillLoader(skillsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("do not resolve to a regular file")
    }

    @Test
    fun `an absent skills directory yields no bundles`() {
        val bundles = SkillLoader(skillsRoot.resolve("does-not-exist")).loadBundles()

        assertThat(bundles).isEmpty()
    }
}
