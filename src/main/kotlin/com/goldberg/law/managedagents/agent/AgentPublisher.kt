package com.goldberg.law.managedagents.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.agents.AgentCreateParams
import com.anthropic.models.beta.agents.AgentListParams
import com.anthropic.models.beta.agents.AgentUpdateParams
import com.anthropic.models.beta.agents.BetaManagedAgentsAgent
import com.goldberg.law.managedagents.ResourcePublisher
import com.goldberg.law.managedagents.ResourceType
import com.fasterxml.jackson.databind.JsonNode

/** Publishes `managed-agents/agents/<name>/agent.yaml`. */
class AgentPublisher(private val client: AnthropicClient) :
    ResourcePublisher<BetaManagedAgentsAgent>(ResourceType.AGENT) {

    private val beta = AnthropicBeta.MANAGED_AGENTS_2026_04_01
    private val agents get() = client.beta().agents()

    override fun create(body: JsonNode): BetaManagedAgentsAgent = agents.create(
        AgentCreateParams.builder()
            .addBeta(beta)
            .body(jsonMapper().convertValue(body, AgentCreateParams.Body::class.java))
            .build()
    )

    /**
     * The update body requires the current version, which doubles as an optimistic-concurrency check:
     * if someone edited the agent in the Console since we listed it, the API rejects the write rather
     * than silently clobbering their change.
     */
    override fun update(existing: BetaManagedAgentsAgent, body: JsonNode): BetaManagedAgentsAgent = agents.update(
        AgentUpdateParams.builder()
            .addBeta(beta)
            .agentId(existing.id())
            .body(jsonMapper().convertValue(body, AgentUpdateParams.Body::class.java))
            .version(existing.version())
            .build()
    )

    override fun list(): Sequence<BetaManagedAgentsAgent> =
        agents.list(AgentListParams.builder().addBeta(beta).build()).autoPager().asSequence()

    override fun nameOf(remote: BetaManagedAgentsAgent): String = remote.name()

    override fun idOf(remote: BetaManagedAgentsAgent): String = remote.id()

    override fun configHashOf(remote: BetaManagedAgentsAgent): String? = hashFromMetadata(remote.metadata())

}
