package com.goldberg.law.skilltool

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.invariantSeparatorsPathString
import kotlin.io.path.relativeTo
import kotlin.io.path.writeText

class SkillLoaderTest {

    @TempDir
    lateinit var agentsRoot: Path

    /** Creates managed-agents/<agent>/skill/ with a SKILL.md (frontmatter name defaults to the agent). */
    private fun writeSkill(
        agent: String,
        frontmatterName: String? = agent,
        extraFiles: List<String> = listOf("references/output-schema.md"),
    ) {
        val skillDir = agentsRoot.resolve(agent).resolve("skill")
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

    private fun bundleFiles(bundle: SkillBundle): List<String> =
        bundle.filePaths.map { it.relativeTo(bundle.rootDir).invariantSeparatorsPathString }

    @Test
    fun `loads every agent that has a skill, sorted by name`() {
        writeSkill("bank-statement-extraction")
        writeSkill("bank-statement-splitting")

        val bundles = SkillLoader(agentsRoot).loadBundles()

        assertThat(bundles.map { it.name })
            .containsExactly("bank-statement-extraction", "bank-statement-splitting")
    }

    @Test
    fun `collects SKILL_md and nested reference files with paths relative to the skill dir`() {
        writeSkill("agent-a", extraFiles = listOf("references/output-schema.md", "references/notes.md"))

        val bundle = SkillLoader(agentsRoot).loadBundles().single()

        assertThat(bundleFiles(bundle))
            .containsExactly("SKILL.md", "references/notes.md", "references/output-schema.md")
    }

    @Test
    fun `skips hidden and junk files`() {
        writeSkill("agent-a")
        val skillDir = agentsRoot.resolve("agent-a").resolve("skill")
        skillDir.resolve(".DS_Store").writeText("junk")
        skillDir.resolve("references/.hidden").writeText("junk")

        val bundle = SkillLoader(agentsRoot).loadBundles().single()

        assertThat(bundleFiles(bundle)).noneMatch { it.contains(".DS_Store") || it.contains(".hidden") }
    }

    @Test
    fun `ignores directories without a skill subdirectory`() {
        writeSkill("real-agent")
        Files.createDirectories(agentsRoot.resolve("not-an-agent"))

        val bundles = SkillLoader(agentsRoot).loadBundles()

        assertThat(bundles.map { it.name }).containsExactly("real-agent")
    }

    @Test
    fun `agents filter restricts to the named agents`() {
        writeSkill("agent-a")
        writeSkill("agent-b")

        val bundles = SkillLoader(agentsRoot).loadBundles(only = listOf("agent-b"))

        assertThat(bundles.map { it.name }).containsExactly("agent-b")
    }

    @Test
    fun `unknown agent name in filter throws`() {
        writeSkill("agent-a")

        assertThatThrownBy { SkillLoader(agentsRoot).loadBundles(only = listOf("nope")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no agent named 'nope'")
    }

    @Test
    fun `frontmatter name that differs from the directory name is rejected`() {
        writeSkill("agent-a", frontmatterName = "wrong-name")

        assertThatThrownBy { SkillLoader(agentsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("'wrong-name'")
            .hasMessageContaining("must match the agent directory name")
    }

    @Test
    fun `missing frontmatter name is rejected`() {
        writeSkill("agent-a", frontmatterName = null)

        assertThatThrownBy { SkillLoader(agentsRoot).loadBundles() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("(missing)")
    }
}
