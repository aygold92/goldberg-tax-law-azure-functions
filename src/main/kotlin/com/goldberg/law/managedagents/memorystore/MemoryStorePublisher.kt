package com.goldberg.law.managedagents.memorystore

import com.anthropic.client.AnthropicClient
import com.anthropic.core.jsonMapper
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.memorystores.BetaManagedAgentsMemoryStore
import com.anthropic.models.beta.memorystores.MemoryStoreCreateParams
import com.anthropic.models.beta.memorystores.MemoryStoreListParams
import com.anthropic.models.beta.memorystores.MemoryStoreUpdateParams
import com.fasterxml.jackson.databind.JsonNode
import com.goldberg.law.managedagents.ResourcePublisher
import com.goldberg.law.managedagents.ResourceType

/**
 * Publishes `managed-agents/memory-stores/<name>.yaml`.
 *
 * Memory stores use their own beta header rather than the managed-agents one — the API rejects a
 * request carrying both.
 *
 * A store's name determines the path it is mounted at (`/mnt/memory/<name>/`), which the skills
 * address as a literal string. Renaming one therefore silently detaches it from the skill that reads
 * it, which is why the loader lints mount paths against the defined stores.
 */
class MemoryStorePublisher(private val client: AnthropicClient) :
    ResourcePublisher<BetaManagedAgentsMemoryStore>(ResourceType.MEMORY_STORE) {

    private val beta = AnthropicBeta.AGENT_MEMORY_2026_07_22
    private val memoryStores get() = client.beta().memoryStores()

    override fun create(body: JsonNode): BetaManagedAgentsMemoryStore = memoryStores.create(
        MemoryStoreCreateParams.builder()
            .addBeta(beta)
            .body(jsonMapper().convertValue(body, MemoryStoreCreateParams.Body::class.java))
            .build()
    )

    override fun update(
        existing: BetaManagedAgentsMemoryStore,
        body: JsonNode,
    ): BetaManagedAgentsMemoryStore = memoryStores.update(
        MemoryStoreUpdateParams.builder()
            .addBeta(beta)
            .memoryStoreId(existing.id())
            .body(jsonMapper().convertValue(body, MemoryStoreUpdateParams.Body::class.java))
            .build()
    )

    override fun list(): Sequence<BetaManagedAgentsMemoryStore> =
        memoryStores.list(MemoryStoreListParams.builder().addBeta(beta).build()).autoPager().asSequence()

    override fun nameOf(remote: BetaManagedAgentsMemoryStore): String = remote.name()

    override fun idOf(remote: BetaManagedAgentsMemoryStore): String = remote.id()

    override fun configHashOf(remote: BetaManagedAgentsMemoryStore): String? = hashFromMetadata(remote.metadata())
}
