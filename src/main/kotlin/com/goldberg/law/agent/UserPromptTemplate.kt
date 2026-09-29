package com.goldberg.law.agent

/**
 * An agent's `user-prompt.md`, with `{PLACEHOLDER}`s filled at session start.
 *
 * [render] demands an exact match between the template's placeholders and the values given, so a prompt
 * edited in `managed-agents/` without a matching code change fails loudly instead of sending a literal
 * `{BANK_ID}` to the agent. Values are rendered with `toString()`, so a list renders as `[5, 6]`.
 */
class UserPromptTemplate(private val name: String, private val template: String) {
    val placeholders: Set<String> = PLACEHOLDER.findAll(template).map { it.groupValues[1] }.toSet()

    fun render(values: Map<String, Any>): String {
        val missing = placeholders - values.keys
        val unused = values.keys - placeholders
        require(missing.isEmpty() && unused.isEmpty()) {
            "User prompt for $name does not match its values: missing $missing, unused $unused"
        }
        return PLACEHOLDER.replace(template) { values.getValue(it.groupValues[1]).toString() }
    }

    companion object {
        private val PLACEHOLDER = Regex("""\{([A-Z_]+)}""")

        /** Loads from the classpath, where `processResources` copies `managed-agents/agents/<name>/user-prompt.md`. */
        fun load(agent: ManagedAgent): UserPromptTemplate =
            UserPromptTemplate(agent.agentName, classpathText("managed-agents/agents/${agent.agentName}/user-prompt.md"))

        /** A `managed-agents/` file that `processResources` ships in the jar. */
        fun classpathText(path: String): String =
            UserPromptTemplate::class.java.classLoader.getResource(path)?.readText()
                ?: error("$path not found on the classpath")
    }
}
