package com.goldberg.law.managedagents

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test

class CliOptionsTest {

    @Test
    fun `defaults to every resource type and no filters`() {
        val opts = CliOptions.parse(arrayOf())

        assertThat(opts.resourceTypes).containsExactlyInAnyOrderElementsOf(ResourceType.entries)
        assertThat(opts.agents).isEmpty()
        assertThat(opts.dryRun).isFalse()
    }

    @Test
    fun `parses all flags`() {
        val opts = CliOptions.parse(
            arrayOf("--root", "/tmp/ma", "--agents", "a, b", "--resource-types", "skill,agent", "--dry-run")
        )

        assertThat(opts.root.toString()).isEqualTo("/tmp/ma")
        assertThat(opts.agents).containsExactly("a", "b")
        assertThat(opts.resourceTypes).containsExactlyInAnyOrder(ResourceType.SKILL, ResourceType.AGENT)
        assertThat(opts.dryRun).isTrue()
    }

    @Test
    fun `a flag missing its value reports which flag`() {
        assertThatThrownBy { CliOptions.parse(arrayOf("--agents")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("--agents requires a value")
    }

    @Test
    fun `an unknown flag is rejected`() {
        assertThatThrownBy { CliOptions.parse(arrayOf("--nope")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("unknown argument: --nope")
    }

    @Test
    fun `an unknown resource type in only is rejected`() {
        assertThatThrownBy { CliOptions.parse(arrayOf("--resource-types", "widgets")) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("Unknown resource type 'widgets'")
    }

    @Test
    fun `skills are applied before the agents that reference them`() {
        val order = ResourceType.APPLY_ORDER

        assertThat(order.indexOf(ResourceType.SKILL)).isLessThan(order.indexOf(ResourceType.AGENT))
        assertThat(order.indexOf(ResourceType.AGENT)).isLessThan(order.indexOf(ResourceType.DEPLOYMENT))
        assertThat(order.indexOf(ResourceType.MEMORY_STORE)).isLessThan(order.indexOf(ResourceType.DEPLOYMENT))
        assertThat(order.indexOf(ResourceType.ENVIRONMENT)).isLessThan(order.indexOf(ResourceType.DEPLOYMENT))
        assertThat(order).containsExactlyInAnyOrderElementsOf(ResourceType.entries)
    }
}
