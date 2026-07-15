package com.goldberg.law.skilltool

import java.nio.file.Path
import kotlin.io.path.Path

/**
 * Parsed command-line options for the skill uploader.
 *
 *   --agents-root <dir>   root containing one directory per agent (default: managed-agents)
 *   --agents <a,b,c>      restrict to these agent directory names (default: all)
 *   --dry-run             lint + list files without publishing
 */
data class UploaderOptions(
    val agentsRoot: Path,
    val agents: List<String>,
    val dryRun: Boolean,
) {
    companion object {
        fun parse(args: Array<String>): UploaderOptions {
            var root = Path(DEFAULT_AGENT_ROOT)
            var agents = emptyList<String>()
            var dryRun = false
            var i = 0
            while (i < args.size) {
                when (val arg = args[i]) {
                    "--agents-root" -> root = Path(args[++i])
                    "--agents" -> agents = args[++i].split(",").map { it.trim() }.filter { it.isNotEmpty() }
                    "--dry-run" -> dryRun = true
                    else -> error("unknown argument: $arg")
                }
                i++
            }
            return UploaderOptions(root, agents, dryRun)
        }

        const val DEFAULT_AGENT_ROOT = "managed-agents"
    }
}
