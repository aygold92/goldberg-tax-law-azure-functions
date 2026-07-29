package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.goldberg.law.managedagents.skill.SkillPublisher
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.mockito.kotlin.mock
import java.nio.file.Path
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText

class DeferredValuesTest {

    private val skillPublisher = SkillPublisher(mock())
    private val resourceRegistry = ResourceRegistry(mapOf(), skillPublisher)

    @TempDir
    lateinit var root: Path

    private val yaml = ObjectMapper(YAMLFactory())

    private fun parse(text: String) = yaml.readTree(text.trimIndent())

    private fun writeFile(relative: String, content: String): Path {
        val path = root.resolve(relative)
        path.parent.createDirectories()
        path.writeText(content)
        return path
    }

    // ---- {file: ...} -------------------------------------------------------

    @Test
    fun `resolves a file construct to the file's text`() {
        writeFile("agents/a/system-prompt.md", "You are a test agent.\n")

        val resolved = DeferredValues.resolveFiles(
            parse("system: {file: system-prompt.md}"),
            root.resolve("agents/a"),
            root,
        )

        assertThat(resolved.get("system").asText()).isEqualTo("You are a test agent.")
    }

    @Test
    fun `resolves file constructs nested inside arrays and objects`() {
        writeFile("deployments/msg.md", "hello")

        val resolved = DeferredValues.resolveFiles(
            parse(
                """
                initial_events:
                  - type: user.message
                    content:
                      - type: text
                        text: {file: msg.md}
                """
            ),
            root.resolve("deployments"),
            root,
        )

        assertThat(resolved.at("/initial_events/0/content/0/text").asText()).isEqualTo("hello")
    }

    @Test
    fun `file paths resolve relative to the containing yaml, not the root`() {
        writeFile("agents/memory-consolidation/request.md", "consolidate please")

        val resolved = DeferredValues.resolveFiles(
            parse("text: {file: ../agents/memory-consolidation/request.md}"),
            root.resolve("deployments"),
            root,
        )

        assertThat(resolved.get("text").asText()).isEqualTo("consolidate please")
    }

    @Test
    fun `an object with a file key alongside others is left alone`() {
        // A real API body may legitimately carry a `file` field; only the exact one-key shape is ours.
        val node = parse("resources: [{type: file, file: keep-me, mount_path: /x}]")

        val resolved = DeferredValues.resolveFiles(node, root, root)

        assertThat(resolved.at("/resources/0/file").asText()).isEqualTo("keep-me")
    }

    @Test
    fun `a missing file target is rejected`() {
        assertThatThrownBy {
            DeferredValues.resolveFiles(parse("system: {file: nope.md}"), root, root)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("does not exist")
    }

    @Test
    fun `a file path escaping the managed-agents root is rejected`() {
        val agentDir = root.resolve("agents/a")
        agentDir.createDirectories()

        assertThatThrownBy {
            DeferredValues.resolveFiles(parse("system: {file: ../../../etc/passwd}"), agentDir, root)
        }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("escapes the managed-agents root")
    }

    // ---- {resource: ...} ---------------------------------------------------

    @Test
    fun `collects resource references at any depth`() {
        val node = parse(
            """
            agent: {resource: agent, name: memory-consolidation}
            resources:
              - type: memory_store
                memory_store_id: {resource: memory-store, name: bank-patterns}
            """
        )

        assertThat(DeferredValues.collectReferences(node)).containsExactly(
            ResourceRef(ResourceType.AGENT, "memory-consolidation"),
            ResourceRef(ResourceType.MEMORY_STORE, "bank-patterns"),
        )
    }

    @Test
    fun `resolves resource references to registry ids`() {
        resourceRegistry.register(ResourceType.SKILL, "bank-statement-extraction", "skill_123")

        val node = parse("skills: [{type: custom, skill_id: {resource: skill, name: bank-statement-extraction}}]")

        val resolved = DeferredValues.resolveReferences(node, resourceRegistry)

        assertThat(resolved.at("/skills/0/skill_id").asText()).isEqualTo("skill_123")
    }

    @Test
    fun `an unresolvable reference is rejected`() {
        val node = parse("agent: {resource: agent, name: ghost}")

        assertThatThrownBy { DeferredValues.resolveReferences(node, resourceRegistry) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("could not resolve")
    }

    @Test
    fun `a malformed resource construct is rejected rather than forwarded to the API`() {
        assertThatThrownBy { DeferredValues.collectReferences(parse("agent: {resource: agent}")) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("malformed {resource:")
    }

    @Test
    fun `an unknown resource type is rejected`() {
        assertThatThrownBy { DeferredValues.collectReferences(parse("x: {resource: widget, name: a}")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown resource type 'widget'")
    }

    @Test
    fun `values that are not constructs pass through untouched`() {
        val node = parse(
            """
            name: keep
            count: 3
            flag: true
            nested: {a: [1, 2]}
            """
        )

        val resolved = DeferredValues.resolveFiles(node, root, root)

        assertThat(resolved).isEqualTo(node)
    }
}
