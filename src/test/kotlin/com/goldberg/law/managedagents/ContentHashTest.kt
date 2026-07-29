package com.goldberg.law.managedagents

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContentHashTest {

    private val yaml = ObjectMapper(YAMLFactory())

    private fun parse(text: String) = yaml.readTree(text.trimIndent())

    @Test
    fun `reordering keys does not change the config hash`() {
        val a = parse(
            """
            name: agent-a
            model: claude-opus-5
            system: hello
            """
        )
        val b = parse(
            """
            system: hello
            model: claude-opus-5
            name: agent-a
            """
        )

        assertThat(ContentHash.ofConfig(a)).isEqualTo(ContentHash.ofConfig(b))
    }

    @Test
    fun `reordering keys inside a nested object does not change the config hash`() {
        val a = parse("config: {type: cloud, networking: {type: limited, allow_package_managers: true}}")
        val b = parse("config: {networking: {allow_package_managers: true, type: limited}, type: cloud}")

        assertThat(ContentHash.ofConfig(a)).isEqualTo(ContentHash.ofConfig(b))
    }

    @Test
    fun `array order is significant`() {
        val a = parse("tools: [{type: one}, {type: two}]")
        val b = parse("tools: [{type: two}, {type: one}]")

        assertThat(ContentHash.ofConfig(a)).isNotEqualTo(ContentHash.ofConfig(b))
    }

    @Test
    fun `changing any value changes the config hash`() {
        val a = parse("name: agent-a\nsystem: hello")
        val b = parse("name: agent-a\nsystem: hello!")

        assertThat(ContentHash.ofConfig(a)).isNotEqualTo(ContentHash.ofConfig(b))
    }

    @Test
    fun `file hash does not depend on the order files are listed in`() {
        val files = listOf(
            "s/SKILL.md" to "one".toByteArray(),
            "s/references/a.md" to "two".toByteArray(),
        )

        assertThat(ContentHash.ofFiles(files)).isEqualTo(ContentHash.ofFiles(files.reversed()))
    }

    @Test
    fun `changing a single byte of a skill file changes the bundle hash`() {
        val before = listOf("s/SKILL.md" to "hello".toByteArray())
        val after = listOf("s/SKILL.md" to "hellp".toByteArray())

        assertThat(ContentHash.ofFiles(before)).isNotEqualTo(ContentHash.ofFiles(after))
    }

    @Test
    fun `renaming a file changes the bundle hash even when contents are identical`() {
        val before = listOf("s/a.md" to "same".toByteArray())
        val after = listOf("s/b.md" to "same".toByteArray())

        assertThat(ContentHash.ofFiles(before)).isNotEqualTo(ContentHash.ofFiles(after))
    }

    @Test
    fun `content cannot shift across the path-content boundary`() {
        // Without a delimiter, ("ab", "c") and ("a", "bc") would hash identically.
        val a = listOf("ab" to "c".toByteArray())
        val b = listOf("a" to "bc".toByteArray())

        assertThat(ContentHash.ofFiles(a)).isNotEqualTo(ContentHash.ofFiles(b))
    }

    @Test
    fun `the hash fits comfortably inside the metadata value limit`() {
        val hash = ContentHash.ofConfig(parse("name: a"))

        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}")
    }
}
