package com.goldberg.law.managedagents

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class ResourceConfigLoaderTest {

    @TempDir
    lateinit var root: Path

    private fun write(relative: String, content: String) {
        val path = root.resolve(relative)
        path.parent.createDirectories()
        path.writeText(content.trimIndent())
    }

    private fun writeSkill(name: String) {
        write("skills/$name/SKILL.md", "---\nname: $name\ndescription: test\n---\n# body")
    }

    private fun writeAgent(dir: String, name: String = dir, skill: String? = null, prompt: String = "be helpful") {
        write("agents/$dir/system-prompt.md", prompt)
        write(
            "agents/$dir/agent.yaml",
            buildString {
                appendLine("name: $name")
                appendLine("model: claude-opus-5")
                appendLine("system: {file: system-prompt.md}")
                if (skill != null) {
                    appendLine("skills:")
                    appendLine("  - {type: custom, skill_id: {resource: skill, name: $skill}}")
                }
            },
        )
    }

    private fun writeMemoryStore(name: String) {
        write("memory-stores/$name.yaml", "name: $name\ndescription: test store")
    }

    private fun writeEnvironment(name: String) {
        write("environments/$name.yaml", "name: $name\nconfig: {type: cloud}")
    }

    private fun writeDeployment(name: String, agent: String, environment: String, stores: List<String> = emptyList()) {
        write(
            "deployments/$name.yaml",
            buildString {
                appendLine("name: $name")
                appendLine("agent: {resource: agent, name: $agent}")
                appendLine("environment_id: {resource: environment, name: $environment}")
                if (stores.isNotEmpty()) {
                    appendLine("resources:")
                    stores.forEach {
                        appendLine("  - {type: memory_store, memory_store_id: {resource: memory-store, name: $it}}")
                    }
                }
            },
        )
    }

    // ---- discovery ---------------------------------------------------------

    @Test
    fun `discovers every resource type`() {
        writeSkill("s1")
        writeAgent("a1", skill = "s1")
        writeMemoryStore("m1")
        writeEnvironment("e1")
        writeDeployment("d1", agent = "a1", environment = "e1")

        val config = ResourceConfigLoader(root).load()

        assertThat(config.skills.map { it.name }).containsExactly("s1")
        assertThat(config.specsOf(ResourceType.AGENT).map { it.name }).containsExactly("a1")
        assertThat(config.specsOf(ResourceType.MEMORY_STORE).map { it.name }).containsExactly("m1")
        assertThat(config.specsOf(ResourceType.ENVIRONMENT).map { it.name }).containsExactly("e1")
        assertThat(config.specsOf(ResourceType.DEPLOYMENT).map { it.name }).containsExactly("d1")
    }

    @Test
    fun `an agent without a skill is loaded`() {
        // Deployment-driven agents such as memory-consolidation have no skill at all.
        writeAgent("consolidator")

        val config = ResourceConfigLoader(root).load()

        assertThat(config.specsOf(ResourceType.AGENT).map { it.name }).containsExactly("consolidator")
        assertThat(config.skills).isEmpty()
    }

    @Test
    fun `a directory without an agent yaml is ignored`() {
        writeAgent("real")
        root.resolve("agents/leftovers").createDirectories()

        val config = ResourceConfigLoader(root).load()

        assertThat(config.specsOf(ResourceType.AGENT).map { it.name }).containsExactly("real")
    }

    @Test
    fun `the system prompt is spliced into the agent body`() {
        writeAgent("a1", prompt = "You are a bank statement agent.")

        val agent = ResourceConfigLoader(root).load().specsOf(ResourceType.AGENT).single()

        assertThat(agent.body.get("system").asText()).isEqualTo("You are a bank statement agent.")
    }

    // ---- name lints --------------------------------------------------------

    @Test
    fun `an agent whose name differs from its directory is rejected`() {
        writeAgent("a1", name = "something-else")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must match the directory name 'a1'")
    }

    @Test
    fun `a memory store whose name differs from its filename is rejected`() {
        write("memory-stores/bank-patterns.yaml", "name: bank_patterns\ndescription: x")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("must match the file name 'bank-patterns'")
    }

    @Test
    fun `malformed yaml reports the offending file`() {
        write("environments/broken.yaml", "name: broken\n  bad: [indent")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("broken.yaml")
    }

    // ---- reference lints ---------------------------------------------------

    @Test
    fun `a reference to an undefined skill is rejected before publishing`() {
        writeAgent("a1", skill = "ghost-skill")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ghost-skill")
            .hasMessageContaining("does not match any skills/ definition")
    }

    @Test
    fun `a deployment referencing an undefined environment is rejected`() {
        writeAgent("a1")
        writeDeployment("d1", agent = "a1", environment = "ghost-env")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("ghost-env")
    }

    // ---- memory mount lint -------------------------------------------------

    @Test
    fun `a skill mounting a memory store that is not defined is rejected`() {
        write(
            "skills/s1/SKILL.md",
            "---\nname: s1\ndescription: test\n---\nRead /mnt/memory/typo-name/ for patterns.",
        )
        writeMemoryStore("bank-patterns")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("/mnt/memory/typo-name/")
            .hasMessageContaining("no memory store is defined")
    }

    @Test
    fun `a system prompt mounting an undefined memory store is rejected`() {
        writeAgent("a1", prompt = "Consolidate /mnt/memory/not-a-store/ nightly.")
        writeMemoryStore("bank-patterns")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("not-a-store")
    }

    @Test
    fun `mount paths matching defined stores pass`() {
        write(
            "skills/s1/SKILL.md",
            "---\nname: s1\ndescription: test\n---\nRead /mnt/memory/bank-patterns/ for patterns.",
        )
        writeMemoryStore("bank-patterns")

        val config = ResourceConfigLoader(root).load()

        assertThat(config.skills.map { it.name }).containsExactly("s1")
    }

    /**
     * Shared markdown lives in `shared/skills/` and reaches a bundle as a symlink, so the lint has to
     * read *through* the link — otherwise a mount path in the one file two skills share is the one that
     * goes unchecked.
     */
    @Test
    fun `the mount lint reads through a symlinked shared file`() {
        writeSkill("s1")
        write("shared/skills/supported-banks-store.md", "Read /mnt/memory/typo-name/ for bank ids.")
        val link = root.resolve("skills/s1/references/supported-banks-store.md")
        link.parent.createDirectories()
        Files.createSymbolicLink(link, link.parent.relativize(root.resolve("shared/skills/supported-banks-store.md")))
        writeMemoryStore("supported-banks")

        assertThatThrownBy { ResourceConfigLoader(root).load() }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("/mnt/memory/typo-name/")
            .hasMessageContaining("no memory store is defined")
    }

    // ---- agent graph filter ------------------------------------------------

    @Test
    fun `filtering to an agent pulls in the skill it references`() {
        writeSkill("s1")
        writeSkill("s2")
        writeAgent("a1", skill = "s1")
        writeAgent("a2", skill = "s2")

        val filtered = ResourceConfigLoader(root).load().filterToSpecifiedAgents(listOf("a1"))

        assertThat(filtered.specsOf(ResourceType.AGENT).map { it.name }).containsExactly("a1")
        assertThat(filtered.skills.map { it.name }).containsExactly("s1")
    }

    @Test
    fun `a skill shared by two agents is selected by either`() {
        writeSkill("shared")
        writeAgent("a1", skill = "shared")
        writeAgent("a2", skill = "shared")

        val viaA1 = ResourceConfigLoader(root).load().filterToSpecifiedAgents(listOf("a1"))
        val viaA2 = ResourceConfigLoader(root).load().filterToSpecifiedAgents(listOf("a2"))

        assertThat(viaA1.skills.map { it.name }).containsExactly("shared")
        assertThat(viaA2.skills.map { it.name }).containsExactly("shared")
    }

    @Test
    fun `filtering to an agent pulls in its deployment and that deployment's dependencies`() {
        writeAgent("consolidator")
        writeEnvironment("sandbox")
        writeMemoryStore("store-a")
        writeMemoryStore("unrelated")
        writeDeployment("nightly", agent = "consolidator", environment = "sandbox", stores = listOf("store-a"))

        val filtered = ResourceConfigLoader(root).load().filterToSpecifiedAgents(listOf("consolidator"))

        assertThat(filtered.specsOf(ResourceType.DEPLOYMENT).map { it.name }).containsExactly("nightly")
        assertThat(filtered.specsOf(ResourceType.ENVIRONMENT).map { it.name }).containsExactly("sandbox")
        assertThat(filtered.specsOf(ResourceType.MEMORY_STORE).map { it.name }).containsExactly("store-a")
    }

    @Test
    fun `an unknown agent name in the filter throws`() {
        writeAgent("a1")

        assertThatThrownBy { ResourceConfigLoader(root).load().filterToSpecifiedAgents(listOf("nope")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("no agent named 'nope'")
    }

    @Test
    fun `an empty filter keeps everything`() {
        writeSkill("s1")
        writeAgent("a1", skill = "s1")

        val filtered = ResourceConfigLoader(root).load().filterToSpecifiedAgents(emptyList())

        assertThat(filtered.specsOf(ResourceType.AGENT)).hasSize(1)
        assertThat(filtered.skills).hasSize(1)
    }
}
