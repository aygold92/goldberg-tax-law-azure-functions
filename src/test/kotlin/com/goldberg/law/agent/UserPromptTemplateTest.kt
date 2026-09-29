package com.goldberg.law.agent

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class UserPromptTemplateTest {

    private val extractionValues = mapOf(
        "START" to 3,
        "END" to 9,
        "BANK_ID" to "bank_of_america",
        "CHECK_PAGES" to listOf(5, 6),
        "FILE_NAME" to "bundle.pdf",
        "SESSION_ID" to "sesn_123",
    )

    @Test
    fun `every agent's prompt is on the classpath with the placeholders the launcher fills`() {
        assertThat(UserPromptTemplate.load(ManagedAgent.SPLITTER).placeholders)
            .containsExactlyInAnyOrder("FILE_NAME", "SESSION_ID")
        assertThat(UserPromptTemplate.load(ManagedAgent.STATEMENT_EXTRACTION).placeholders)
            .containsExactlyInAnyOrder("START", "END", "BANK_ID", "CHECK_PAGES", "FILE_NAME", "SESSION_ID")
        assertThat(UserPromptTemplate.load(ManagedAgent.CHECK_EXTRACTION).placeholders)
            .containsExactlyInAnyOrder("PAGES")
        assertThat(UserPromptTemplate.load(ManagedAgent.MEMORY_CONSOLIDATION).placeholders)
            .containsExactlyInAnyOrder("BANK_ID", "FORMAT")
    }

    @Test
    fun `an inserted value's own braces are left alone`() {
        // The format files carry lowercase placeholders like {bank_id}; they must reach the agent untouched
        val prompt = UserPromptTemplate("test", "{FORMAT}").render(mapOf("FORMAT" to "# {bank_id} {ACCOUNT}"))

        assertThat(prompt).isEqualTo("# {bank_id} {ACCOUNT}")
    }

    @Test
    fun `renders every placeholder, lists via toString`() {
        val prompt = UserPromptTemplate.load(ManagedAgent.STATEMENT_EXTRACTION).render(extractionValues)

        // Asserts on the substituted values only — the surrounding wording belongs to managed-agents/
        assertThat(prompt)
            .contains("3–9")
            .contains("`bank_of_america`")
            .contains("[5, 6]")
            .contains("`bundle.pdf`")
            .contains("`sesn_123`")
            .doesNotContainPattern("""\{[A-Z_]+}""")
    }

    @Test
    fun `an empty list renders as brackets`() {
        val prompt = UserPromptTemplate.load(ManagedAgent.STATEMENT_EXTRACTION)
            .render(extractionValues + ("CHECK_PAGES" to emptyList<Int>()))

        assertThat(prompt).contains("[]")
    }

    @Test
    fun `renders values literally, including regex replacement characters`() {
        val prompt = UserPromptTemplate("test", "File: {FILE_NAME}").render(mapOf("FILE_NAME" to """a$1\b.pdf"""))

        assertThat(prompt).isEqualTo("""File: a$1\b.pdf""")
    }

    @Test
    fun `fails when a placeholder has no value`() {
        assertThatThrownBy {
            UserPromptTemplate.load(ManagedAgent.STATEMENT_EXTRACTION).render(extractionValues - "BANK_ID")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("BANK_ID")
    }

    @Test
    fun `fails when a value has no placeholder`() {
        assertThatThrownBy {
            UserPromptTemplate.load(ManagedAgent.CHECK_EXTRACTION).render(mapOf("PAGES" to listOf(5), "SESSION_ID" to "sesn_1"))
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("SESSION_ID")
    }
}
