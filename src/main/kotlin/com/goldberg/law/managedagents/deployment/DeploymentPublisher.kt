package com.goldberg.law.managedagents.deployment

import com.anthropic.client.AnthropicClient
import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.agents.AgentRetrieveParams
import com.anthropic.models.beta.deployments.BetaManagedAgentsDeployment
import com.anthropic.models.beta.deployments.DeploymentCreateParams
import com.anthropic.models.beta.deployments.DeploymentListParams
import com.anthropic.models.beta.deployments.DeploymentUpdateParams
import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.goldberg.law.managedagents.ResourcePublisher
import com.goldberg.law.managedagents.ResourceType

/**
 * Publishes `managed-agents/deployments/<name>.yaml` — standing, scheduled work.
 *
 * A deployment carries no per-run input (`deployments().run()` takes only an id), so it suits agents
 * that do the same self-contained job on a timer. Agents that need per-invocation parameters are
 * driven through sessions instead and have no deployment file.
 *
 * This is also the one place memory stores are attached declaratively; for session-driven agents the
 * runtime layer attaches them per session.
 *
 * A deployment pins the agent version that was current when it was last written, and an agent update
 * keeps the agent's id — so the deployment's config hash can't see it. When the config names its agent
 * by bare id (what `{resource: agent, …}` resolves to), the deployment is treated as stale whenever
 * that agent has moved past the pinned version, and every write pins the agent's latest version
 * explicitly. A config that spells out `{type: agent, id, version}` is left alone.
 */
class DeploymentPublisher(private val client: AnthropicClient) :
    ResourcePublisher<BetaManagedAgentsDeployment>(ResourceType.DEPLOYMENT) {

    private val beta = AnthropicBeta.MANAGED_AGENTS_2026_04_01
    private val deployments get() = client.beta().deployments()

    /** Agent id → latest version, so the staleness check and the write that follows share one lookup. */
    private val latestAgentVersions = mutableMapOf<String, Int>()

    override fun create(body: JsonNode): BetaManagedAgentsDeployment = deployments.create(
        DeploymentCreateParams.builder()
            .addBeta(beta)
            .body(jsonMapper().convertValue(pinLatestAgentVersion(body), DeploymentCreateParams.Body::class.java))
            .build()
    )

    override fun update(
        existing: BetaManagedAgentsDeployment,
        body: JsonNode,
    ): BetaManagedAgentsDeployment = deployments.update(
        DeploymentUpdateParams.builder()
            .addBeta(beta)
            .deploymentId(existing.id())
            .body(jsonMapper().convertValue(pinLatestAgentVersion(body), DeploymentUpdateParams.Body::class.java))
            .build()
    )

    override fun list(): Sequence<BetaManagedAgentsDeployment> =
        deployments.list(DeploymentListParams.builder().addBeta(beta).build()).autoPager().asSequence()

    override fun nameOf(remote: BetaManagedAgentsDeployment): String = remote.name()

    override fun idOf(remote: BetaManagedAgentsDeployment): String = remote.id()

    override fun configHashOf(remote: BetaManagedAgentsDeployment): String? = hashFromMetadata(remote.metadata())

    override fun isStale(existing: BetaManagedAgentsDeployment, config: ObjectNode): Boolean {
        val agentId = config.get(AGENT_KEY)?.takeIf { it.isTextual }?.asText() ?: return false
        val pinned = existing.agent()
        if (pinned.id() != agentId) return true

        val latest = latestAgentVersion(agentId)
        if (pinned.version() == latest) return false
        logger.info { "${existing.name()} pins agent v${pinned.version()}, latest is v$latest" }
        return true
    }

    /** Replaces a bare agent id with `{type: agent, id, version}` at the agent's latest version. */
    private fun pinLatestAgentVersion(body: JsonNode): JsonNode {
        val agentId = body.get(AGENT_KEY)?.takeIf { it.isTextual }?.asText() ?: return body
        return (body as ObjectNode).deepCopy().also {
            it.putObject(AGENT_KEY)
                .put("type", "agent")
                .put("id", agentId)
                .put("version", latestAgentVersion(agentId))
        }
    }

    private fun latestAgentVersion(agentId: String): Int = latestAgentVersions.getOrPut(agentId) {
        client.beta().agents().retrieve(agentId, AgentRetrieveParams.builder().addBeta(beta).build()).version()
    }

    private companion object {
        const val AGENT_KEY = "agent"
    }
}
