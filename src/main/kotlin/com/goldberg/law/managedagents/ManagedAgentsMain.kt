package com.goldberg.law.managedagents

import com.anthropic.client.AnthropicClient
import com.anthropic.client.okhttp.AnthropicOkHttpClient
import com.goldberg.law.managedagents.agent.AgentPublisher
import com.goldberg.law.managedagents.deployment.DeploymentPublisher
import com.goldberg.law.managedagents.environment.EnvironmentPublisher
import com.goldberg.law.managedagents.memorystore.MemoryStorePublisher
import com.goldberg.law.managedagents.skill.SkillPublisher
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlin.io.path.relativeTo

/**
 * Logging reaches JUL via slf4j-jdk14, whose SimpleFormatter splits every record across two lines:
 * timestamp and source, then level and message. This is the stock format with that line break
 * replaced by a space, so each record keeps its detail but occupies one line.
 *
 * It uses the logger name (`%3$s`) where the stock format uses the inferred source (`%2$s`). JUL
 * infers the source from the stack, which yields the class that *declares* the logging method — so
 * every subclass of [ResourcePublisher] would report as `ResourcePublisher`, and an agent would be
 * indistinguishable from a deployment. The logger name is set at construction and is always the real
 * class. The trade is losing the method name, which was only ever `apply`/`publish`/`main`.
 *
 * Applied before the logger below is created, since SimpleFormatter latches this at class-init —
 * declaration order here is load-bearing. A caller that sets its own format keeps it.
 */
private val loggingFormat = run {
    val property = "java.util.logging.SimpleFormatter.format"
    if (System.getProperty(property) == null) {
        System.setProperty(property, "%1\$tb %1\$td, %1\$tY %1\$tl:%1\$tM:%1\$tS %1\$Tp %3\$s %4\$s: %5\$s%6\$s%n")
    }
}

private val logger = KotlinLogging.logger {}

/**
 * Entry point: reconciles the Anthropic workspace with `managed-agents/`. Invoked via the
 * `applyAgents` Gradle task, which injects `ANTHROPIC_API_KEY` from the local settings JSON.
 *
 * Everything is loaded and linted before the first network call, so a typo anywhere fails the run
 * rather than leaving the workspace half-updated. Resources are then applied in dependency order,
 * each stage registering the ids the next stage's `{resource: …}` constructs resolve against.
 */
fun main(args: Array<String>) {
    val opts = CliOptions.parse(args)

    val config = ResourceConfigLoader(opts.root).load().filterToSpecifiedAgents(opts.agents)
    if (config.isEmpty()) {
        logger.warn { "Nothing to apply under ${opts.root}" }
        return
    }

    val client = runCatching { AnthropicOkHttpClient.fromEnv() }.getOrElse {
        error("Could not build Anthropic client. Is ANTHROPIC_API_KEY set? (${it.message})")
    }
    try {
        apply(opts, config, client)
    } finally {
        client.close()
    }
}

private fun apply(opts: CliOptions, config: ManagedAgentsResourceConfig, client: AnthropicClient) {
    val publishers = mapOf(
        ResourceType.MEMORY_STORE to MemoryStorePublisher(client),
        ResourceType.ENVIRONMENT to EnvironmentPublisher(client),
        ResourceType.AGENT to AgentPublisher(client),
        ResourceType.DEPLOYMENT to DeploymentPublisher(client),
    )

    // skills have different API format as they need to upload a file bundle, so we instantiate separately
    val skillPublisher = SkillPublisher(client)

    val registry = ResourceRegistry(publishers, skillPublisher)

    val outcomes = mutableListOf<Pair<String, Outcome>>()

    ResourceType.APPLY_ORDER.filter(opts::includes).forEach { type ->
        if (type == ResourceType.SKILL) {
            config.skills.forEach { bundle ->
                if (opts.dryRun) {
                    val files = bundle.filePaths.joinToString { it.relativeTo(bundle.rootDir).toString() }
                    logger.info { "skill ${bundle.name} — ${bundle.filePaths.size} files: $files" }
                } else {
                    val result = skillPublisher.publish(bundle)
                    registry.register(ResourceType.SKILL, bundle.name, result.id)
                    outcomes += "${type.refName} ${bundle.name}" to result.outcome
                }
            }
        } else {
            val publisher = publishers.getValue(type)
            config.specsOf(type).forEach { spec ->
                if (opts.dryRun) {
                    val refs = spec.references.takeIf { it.isNotEmpty() }
                        ?.joinToString(prefix = " → ") { "${it.type.refName}:${it.name}" }
                        .orEmpty()
                    logger.info { "${type.refName} ${spec.name} (${opts.root.relativize(spec.source)})$refs" }
                } else {
                    val result = publisher.apply(spec, registry)
                    registry.register(type, spec.name, result.id)
                    outcomes += "${type.refName} ${spec.name}" to result.outcome
                }
            }
        }
    }

    if (opts.dryRun) {
        logger.info { "Dry run — nothing published." }
    } else {
        val counts = outcomes.groupingBy { it.second }.eachCount()
        logger.info {
            "Done — ${counts[Outcome.CREATED] ?: 0} created, ${counts[Outcome.UPDATED] ?: 0} updated, " +
                    "${counts[Outcome.UNCHANGED] ?: 0} unchanged."
        }
    }
}
