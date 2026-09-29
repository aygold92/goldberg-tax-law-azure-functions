package com.goldberg.law.agent

import com.anthropic.client.AnthropicClient
import com.goldberg.law.managedagents.ResourcePublisher
import com.goldberg.law.managedagents.ResourceType
import com.goldberg.law.managedagents.agent.AgentPublisher
import com.goldberg.law.managedagents.environment.EnvironmentPublisher
import com.goldberg.law.managedagents.memorystore.MemoryStorePublisher
import com.google.inject.Inject
import com.google.inject.Singleton
import java.util.concurrent.ConcurrentHashMap

/**
 * Resolves `managed-agents/` resource names to their workspace ids, by the same list-and-match-by-name the
 * `applyAgents` publishers use. Ids are stable across updates (an update adds a version, not a new id), so
 * each is looked up once per process.
 */
@Singleton
class ManagedAgentResourceResolver @Inject constructor(client: AnthropicClient) {
    private val publishers: Map<ResourceType, ResourcePublisher<*>> = mapOf(
        ResourceType.AGENT to AgentPublisher(client),
        ResourceType.ENVIRONMENT to EnvironmentPublisher(client),
        ResourceType.MEMORY_STORE to MemoryStorePublisher(client),
    )
    private val ids = ConcurrentHashMap<Pair<ResourceType, String>, String>()

    fun agentId(agent: ManagedAgent): String = resolve(ResourceType.AGENT, agent.agentName)

    fun environmentId(): String = resolve(ResourceType.ENVIRONMENT, ManagedAgent.ENVIRONMENT)

    fun memoryStoreId(name: String): String = resolve(ResourceType.MEMORY_STORE, name)

    private fun resolve(type: ResourceType, name: String): String = ids.computeIfAbsent(type to name) {
        publishers.getValue(type).lookupId(name)
            ?: throw IllegalStateException("No ${type.refName} named '$name' in the Anthropic workspace — run `gradle applyAgents`")
    }
}
