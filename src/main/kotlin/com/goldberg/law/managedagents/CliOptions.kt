package com.goldberg.law.managedagents

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Parsed command-line options for the managed-agents publisher.
 *
 *   --root <dir>        root containing one directory per resource type (default: managed-agents)
 *   --agents <a,b,c>    restrict to these agents and everything they reference (default: all)
 *   --only <types>      restrict to these resource types, e.g. `skills,agents` (default: all)
 *   --dry-run           load, lint and print the plan without publishing
 */
data class CliOptions(
    val root: Path,
    val agents: List<String>,
    val resourceTypes: Set<ResourceType>,
    val dryRun: Boolean,
) {
    fun includes(type: ResourceType) = type in resourceTypes

    companion object {
        const val DEFAULT_ROOT = "managed-agents"

        fun parse(args: Array<String>): CliOptions {
            var root = Path(DEFAULT_ROOT)
            var agents = emptyList<String>()
            var resourceTypes = ResourceType.entries.toSet()
            var dryRun = false

            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--root" -> root = Path(args.value(++i, arg))
                    "--agents" -> agents = args.value(++i, arg).splitList()
                    "--resource-types" -> resourceTypes = args.value(++i, arg).splitList().map(ResourceType::fromRefName).toSet()
                    "--dry-run" -> dryRun = true
                    else -> error("unknown argument: $arg")
                }
                i++
            }
            require(resourceTypes.isNotEmpty()) { "--resource-types selected no resource types" }
            return CliOptions(root, agents, resourceTypes, dryRun)
        }

        private fun Array<String>.value(index: Int, flag: String): String =
            getOrNull(index) ?: error("$flag requires a value")

        private fun String.splitList() = split(",").map { it.trim() }.filter { it.isNotEmpty() }
    }
}
