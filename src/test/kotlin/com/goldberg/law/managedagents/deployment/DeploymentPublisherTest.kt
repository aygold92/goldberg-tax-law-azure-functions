package com.goldberg.law.managedagents.deployment

import com.anthropic.client.AnthropicClient
import com.anthropic.core.AutoPager
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.agents.AgentRetrieveParams
import com.anthropic.models.beta.agents.BetaManagedAgentsAgent
import com.anthropic.models.beta.agents.BetaManagedAgentsAgentReference
import com.anthropic.models.beta.deployments.BetaManagedAgentsDeployment
import com.anthropic.models.beta.deployments.DeploymentCreateParams
import com.anthropic.models.beta.deployments.DeploymentListPage
import com.anthropic.models.beta.deployments.DeploymentListParams
import com.anthropic.models.beta.deployments.DeploymentUpdateParams
import com.anthropic.services.blocking.BetaService
import com.anthropic.services.blocking.beta.AgentService
import com.anthropic.services.blocking.beta.DeploymentService
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory
import com.goldberg.law.managedagents.ContentHash
import com.goldberg.law.managedagents.Outcome
import com.goldberg.law.managedagents.ResourceRegistry
import com.goldberg.law.managedagents.ResourceSpec
import com.goldberg.law.managedagents.ResourceType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.nio.file.Path

/**
 * A deployment pins the agent version current when it was last written, and an agent update keeps the
 * agent's id, so an unchanged deployment config must still be re-applied once its agent moves on.
 */
class DeploymentPublisherTest {

    private val agentId = "agent_123"
    private val deploymentId = "depl_456"

    private val config = """
        name: memory-consolidation-splitting
        agent: $agentId
        environment_id: env_789
        initial_events:
          - type: user.message
            content: [{type: text, text: Consolidate.}]
    """

    private val agents = mock<AgentService>()
    private val deployments = mock<DeploymentService>()
    private val beta = mock<BetaService> {
        on { agents() } doReturn agents
        on { deployments() } doReturn deployments
    }
    private val client = mock<AnthropicClient> { on { beta() } doReturn beta }
    private val registry = ResourceRegistry(emptyMap(), mock())

    private fun parse(text: String) = ObjectMapper(YAMLFactory()).readTree(text.trimIndent()) as ObjectNode

    private fun spec(body: ObjectNode) =
        ResourceSpec(ResourceType.DEPLOYMENT, body.get("name").asText(), Path.of("unused.yaml"), body, emptyList())

    private fun latestAgentVersion(version: Int) {
        val agent = mock<BetaManagedAgentsAgent> { on { version() } doReturn version }
        whenever(agents.retrieve(eq(agentId), any<AgentRetrieveParams>())).thenReturn(agent)
    }

    private fun remoteDeployment(configHash: String?, pinnedVersion: Int): BetaManagedAgentsDeployment {
        val metadata = BetaManagedAgentsDeployment.Metadata.builder()
            .apply { configHash?.let { putAdditionalProperty(ContentHash.METADATA_KEY, JsonValue.from(it)) } }
            .build()
        val agentRef = BetaManagedAgentsAgentReference.builder()
            .id(agentId)
            .type(BetaManagedAgentsAgentReference.Type.AGENT)
            .version(pinnedVersion)
            .build()
        return mock {
            on { id() } doReturn deploymentId
            on { name() } doReturn "memory-consolidation-splitting"
            on { metadata() } doReturn metadata
            on { agent() } doReturn agentRef
        }
    }

    private fun remoteDeployments(vararg existing: BetaManagedAgentsDeployment) {
        val pager = mock<AutoPager<BetaManagedAgentsDeployment>> { on { iterator() } doReturn existing.iterator() }
        val page = mock<DeploymentListPage> { on { autoPager() } doReturn pager }
        whenever(deployments.list(any<DeploymentListParams>())).thenReturn(page)
        whenever(deployments.update(any<DeploymentUpdateParams>())).thenReturn(existing.firstOrNull())
    }

    private fun capturedUpdate(): DeploymentUpdateParams =
        argumentCaptor<DeploymentUpdateParams>().also { verify(deployments).update(it.capture()) }.firstValue

    @Test
    fun `skips a deployment whose config is unchanged and whose agent is still at the pinned version`() {
        val body = parse(config)
        latestAgentVersion(3)
        remoteDeployments(remoteDeployment(ContentHash.ofConfig(body), pinnedVersion = 3))

        val result = DeploymentPublisher(client).apply(spec(body), registry)

        assertThat(result.outcome).isEqualTo(Outcome.UNCHANGED)
        verify(deployments, never()).update(any<DeploymentUpdateParams>())
    }

    @Test
    fun `updates an unchanged deployment when its agent has a newer version, pinning the latest`() {
        val body = parse(config)
        latestAgentVersion(4)
        remoteDeployments(remoteDeployment(ContentHash.ofConfig(body), pinnedVersion = 3))

        val result = DeploymentPublisher(client).apply(spec(body), registry)

        assertThat(result.outcome).isEqualTo(Outcome.UPDATED)
        val agent = capturedUpdate().agent().get().asBetaManagedAgentsAgentParams()
        assertThat(agent.id()).isEqualTo(agentId)
        assertThat(agent.version()).hasValue(4)
    }

    @Test
    fun `updates a deployment whose config changed, pinning the agent's latest version`() {
        val body = parse(config)
        latestAgentVersion(3)
        remoteDeployments(remoteDeployment("stale-hash", pinnedVersion = 3))

        val result = DeploymentPublisher(client).apply(spec(body), registry)

        assertThat(result.outcome).isEqualTo(Outcome.UPDATED)
        assertThat(capturedUpdate().agent().get().asBetaManagedAgentsAgentParams().version()).hasValue(3)
    }

    @Test
    fun `leaves a deployment alone when its config pins an explicit agent version`() {
        val body = parse(
            """
            name: memory-consolidation-splitting
            agent: {type: agent, id: $agentId, version: 3}
            environment_id: env_789
            """
        )
        latestAgentVersion(4)
        remoteDeployments(remoteDeployment(ContentHash.ofConfig(body), pinnedVersion = 3))

        val result = DeploymentPublisher(client).apply(spec(body), registry)

        assertThat(result.outcome).isEqualTo(Outcome.UNCHANGED)
        verify(agents, never()).retrieve(any<String>(), any<AgentRetrieveParams>())
    }

    @Test
    fun `creates a missing deployment pinned to the agent's latest version`() {
        val body = parse(config)
        latestAgentVersion(4)
        remoteDeployments()
        val created = remoteDeployment(null, pinnedVersion = 4)
        whenever(deployments.create(any<DeploymentCreateParams>())).thenReturn(created)

        val result = DeploymentPublisher(client).apply(spec(body), registry)

        assertThat(result.outcome).isEqualTo(Outcome.CREATED)
        val params = argumentCaptor<DeploymentCreateParams>().also { verify(deployments).create(it.capture()) }.firstValue
        assertThat(params.agent().asBetaManagedAgentsAgentParams().version()).hasValue(4)
    }
}
