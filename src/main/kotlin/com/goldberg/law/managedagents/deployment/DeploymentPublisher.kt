package com.goldberg.law.managedagents.deployment

import com.anthropic.client.AnthropicClient
import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.deployments.BetaManagedAgentsDeployment
import com.anthropic.models.beta.deployments.DeploymentCreateParams
import com.anthropic.models.beta.deployments.DeploymentListParams
import com.anthropic.models.beta.deployments.DeploymentUpdateParams
import com.fasterxml.jackson.databind.JsonNode
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
 */
class DeploymentPublisher(private val client: AnthropicClient) :
    ResourcePublisher<BetaManagedAgentsDeployment>(ResourceType.DEPLOYMENT) {

    private val beta = AnthropicBeta.MANAGED_AGENTS_2026_04_01
    private val deployments get() = client.beta().deployments()

    override fun create(body: JsonNode): BetaManagedAgentsDeployment = deployments.create(
        DeploymentCreateParams.builder()
            .addBeta(beta)
            .body(jsonMapper().convertValue(body, DeploymentCreateParams.Body::class.java))
            .build()
    )

    override fun update(
        existing: BetaManagedAgentsDeployment,
        body: JsonNode,
    ): BetaManagedAgentsDeployment = deployments.update(
        DeploymentUpdateParams.builder()
            .addBeta(beta)
            .deploymentId(existing.id())
            .body(jsonMapper().convertValue(body, DeploymentUpdateParams.Body::class.java))
            .build()
    )

    override fun list(): Sequence<BetaManagedAgentsDeployment> =
        deployments.list(DeploymentListParams.builder().addBeta(beta).build()).autoPager().asSequence()

    override fun nameOf(remote: BetaManagedAgentsDeployment): String = remote.name()

    override fun idOf(remote: BetaManagedAgentsDeployment): String = remote.id()

    override fun configHashOf(remote: BetaManagedAgentsDeployment): String? = hashFromMetadata(remote.metadata())
}
