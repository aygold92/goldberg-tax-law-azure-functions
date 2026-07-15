package com.goldberg.law.skilltool

import com.anthropic.client.okhttp.AnthropicOkHttpClient
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.relativeTo

private val logger = KotlinLogging.logger {}

/**
 * Entry point: publishes each agent's skill (`managed-agents/<agent>/skill/`) to the Anthropic
 * Skills API. Invoked via the `updateSkill` Gradle task; requires `ANTHROPIC_API_KEY` in the
 * environment (the task injects it from the local settings JSON).
 *
 * The pieces are deliberately small and single-purpose:
 *  - [UploaderOptions] parses the CLI args
 *  - [SkillLoader] discovers + validates skills on disk into [SkillBundle]s
 *  - [SkillPublisher] talks to the API
 */
fun main(args: Array<String>) {
    val opts = UploaderOptions.parse(args)

    // Lint + load everything up front so a bad skill fails before anything is published.
    val skills = SkillLoader(opts.agentsRoot).loadBundles(opts.agents)
    if (skills.isEmpty()) {
        logger.warn { "No matching skills found under ${opts.agentsRoot}" }
        return
    }

    if (opts.dryRun) {
        skills.forEach { bundle ->
            logger.info { "${bundle.name} — ${bundle.filePaths.size} files: ${bundle.filePaths.map {it.relativeTo(bundle.rootDir) }.joinToString()}" }
        }
        logger.info { "Dry run — nothing published." }
        return
    }

    val client = runCatching { AnthropicOkHttpClient.fromEnv() }.getOrElse {
        error("Could not build Anthropic client. Is ANTHROPIC_API_KEY set? (${it.message})")
    }
    try {
        val publisher = SkillPublisher(client)
        skills.forEach { publisher.publish(it) }
    } finally {
        client.close()
    }
}
