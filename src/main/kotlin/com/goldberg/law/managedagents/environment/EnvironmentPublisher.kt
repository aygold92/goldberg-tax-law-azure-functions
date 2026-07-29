package com.goldberg.law.managedagents.environment

import com.anthropic.client.AnthropicClient
import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.environments.BetaEnvironment
import com.anthropic.models.beta.environments.EnvironmentCreateParams
import com.anthropic.models.beta.environments.EnvironmentListParams
import com.anthropic.models.beta.environments.EnvironmentUpdateParams
import com.fasterxml.jackson.databind.JsonNode
import com.goldberg.law.managedagents.ResourcePublisher
import com.goldberg.law.managedagents.ResourceType

/**
 * Publishes `managed-agents/environments/<name>.yaml` — the sandbox agents run in.
 *
 * Environments are shared: several agents' sessions and deployments can name the same one, which is
 * why they live at the top level rather than inside an agent directory.
 */
class EnvironmentPublisher(private val client: AnthropicClient) :
    ResourcePublisher<BetaEnvironment>(ResourceType.ENVIRONMENT) {

    private val beta = AnthropicBeta.MANAGED_AGENTS_2026_04_01
    private val environments get() = client.beta().environments()

    override fun create(body: JsonNode): BetaEnvironment = environments.create(
        EnvironmentCreateParams.builder()
            .addBeta(beta)
            .body(jsonMapper().convertValue(body, EnvironmentCreateParams.Body::class.java))
            .build()
    )

    override fun update(existing: BetaEnvironment, body: JsonNode): BetaEnvironment = environments.update(
        EnvironmentUpdateParams.builder()
            .addBeta(beta)
            .environmentId(existing.id())
            .body(jsonMapper().convertValue(body, EnvironmentUpdateParams.Body::class.java))
            .build()
    )

    override fun list(): Sequence<BetaEnvironment> =
        environments.list(EnvironmentListParams.builder().addBeta(beta).build()).autoPager().asSequence()

    override fun nameOf(remote: BetaEnvironment): String = remote.name()

    override fun idOf(remote: BetaEnvironment): String = remote.id()

    override fun configHashOf(remote: BetaEnvironment): String? = hashFromMetadata(remote.metadata())
}
