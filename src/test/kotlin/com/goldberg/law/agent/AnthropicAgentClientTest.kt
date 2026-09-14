package com.goldberg.law.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.AutoPager
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.deploymentruns.BetaManagedAgentsDeploymentRun
import com.anthropic.models.beta.deployments.DeploymentRunParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsMemoryStoreResourceParam
import com.anthropic.models.beta.sessions.BetaManagedAgentsSession
import com.anthropic.models.beta.sessions.BetaManagedAgentsSessionAgent
import com.anthropic.models.beta.sessions.SessionCreateParams
import com.anthropic.models.beta.sessions.SessionDeleteParams
import com.anthropic.models.beta.sessions.SessionRetrieveParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsAgentMessageEvent
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionErrorEvent
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionEvent
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionStatusIdleEvent
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsTextBlock
import com.anthropic.models.beta.sessions.events.EventListPage
import com.anthropic.models.beta.sessions.events.EventListParams
import com.anthropic.models.beta.sessions.events.EventSendParams
import com.anthropic.services.blocking.BetaService
import com.anthropic.services.blocking.beta.DeploymentService
import com.anthropic.services.blocking.beta.FileService
import com.anthropic.services.blocking.beta.SessionService
import com.anthropic.services.blocking.beta.sessions.EventService
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.Optional

class AnthropicAgentClientTest {

    private val sessionId = "sesn_1"

    private val events: EventService = mock()
    private val sessions: SessionService = mock { on { events() } doReturn events }
    private val files: FileService = mock()
    private val deployments: DeploymentService = mock()
    private val beta: BetaService = mock {
        on { sessions() } doReturn sessions
        on { files() } doReturn files
        on { deployments() } doReturn deployments
    }
    private val client: AnthropicClient = mock { on { beta() } doReturn beta }
    private val resolver: ManagedAgentResourceResolver = mock {
        on { agentId(any()) } doAnswer { "agent_" + it.getArgument<ManagedAgent>(0).name }
        on { environmentId() } doReturn "env_1"
        on { memoryStoreId(any()) } doAnswer { "store_" + it.getArgument<String>(0) }
        on { deploymentId(any()) } doAnswer { "dep_" + it.getArgument<String>(0) }
    }
    private val agentClient = AnthropicAgentClient(client, resolver)

    private fun givenSessionCreated() {
        val session: BetaManagedAgentsSession = mock { on { id() } doReturn sessionId }
        whenever(sessions.create(any<SessionCreateParams>())).thenReturn(session)
    }

    private fun createdParams(): SessionCreateParams =
        argumentCaptor<SessionCreateParams>().apply { verify(sessions).create(capture()) }.firstValue

    private fun sentPrompt(): String {
        val params = argumentCaptor<EventSendParams>().apply { verify(events).send(capture()) }.firstValue
        assertThat(params.sessionId()).contains(sessionId)
        return params.events().single().asUserMessage().content().single().asText().text()
    }

    // ---- startSession -------------------------------------------------------

    @Test
    fun `a splitter session mounts the bundle and the bank-patterns store read-write`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "Split a.pdf", mapOf("fileId" to "f1"))

        val params = createdParams()
        assertThat(params.agent().asString()).isEqualTo("agent_SPLITTER")
        assertThat(params.environmentId()).isEqualTo("env_1")
        assertThat(params.title()).contains("Split a.pdf")

        val resources = params.resources().get()
        assertThat(resources).hasSize(2)
        val file = resources.single { it.isFile() }.asFile()
        assertThat(file.fileId()).isEqualTo("file_1")
        assertThat(file.mountPath()).contains("/mnt/session/uploads/workspace/bundle.pdf")
        val store = resources.single { it.isMemoryStore() }.asMemoryStore()
        assertThat(store.memoryStoreId()).isEqualTo("store_bank-patterns")
        assertThat(store.access()).contains(BetaManagedAgentsMemoryStoreResourceParam.Access.READ_WRITE)
    }

    @Test
    fun `session metadata names the agent alongside the caller's metadata`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t", mapOf("fileId" to "f1"))

        assertThat(createdParams().metadata().get()._additionalProperties()).containsExactlyInAnyOrderEntriesOf(
            mapOf("agent" to JsonValue.from("SPLITTER"), "fileId" to JsonValue.from("f1"))
        )
    }

    @Test
    fun `an extraction session mounts the extraction-notes store`() {
        givenSessionCreated()

        agentClient.startSession(
            ManagedAgent.STATEMENT_EXTRACTION, "file_1",
            mapOf("START" to 1, "END" to 2, "BANK_ID" to "b", "CHECK_PAGES" to emptyList<Int>(), "FILE_NAME" to "a.pdf"),
            "t",
        )

        val store = createdParams().resources().get().single { it.isMemoryStore() }.asMemoryStore()
        assertThat(store.memoryStoreId()).isEqualTo("store_extraction-notes")
    }

    @Test
    fun `a check extraction session mounts only the bundle`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5, 6)), "t")

        assertThat(createdParams().resources().get()).singleElement().matches { it.isFile() }
    }

    @Test
    fun `the kickoff prompt carries the id of the session it was sent to`() {
        givenSessionCreated()

        val result = agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t")

        assertThat(result).isEqualTo(sessionId)
        assertThat(sentPrompt()).contains(sessionId).contains("a.pdf")
    }

    @Test
    fun `a prompt without a session id placeholder is sent without one`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5, 6)), "t")

        assertThat(sentPrompt()).contains("pages [5, 6]").doesNotContain(sessionId)
    }

    @Test
    fun `a failed kickoff deletes the session rather than leaving it idle`() {
        givenSessionCreated()
        whenever(events.send(any<EventSendParams>())).thenThrow(RuntimeException("boom"))

        assertThatThrownBy {
            agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t")
        }.hasMessage("boom")

        val deleted = argumentCaptor<SessionDeleteParams>().apply { verify(sessions).delete(capture()) }.firstValue
        assertThat(deleted.sessionId()).contains(sessionId)
    }

    @Test
    fun `a prompt that doesn't match its values deletes the session and sends nothing`() {
        givenSessionCreated()

        assertThatThrownBy {
            agentClient.startSession(ManagedAgent.SPLITTER, "file_1", emptyMap(), "t")
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("FILE_NAME")

        verify(sessions).delete(any<SessionDeleteParams>())
        verify(events, never()).send(any<EventSendParams>())
    }

    // ---- runDeployment ------------------------------------------------------

    private fun givenRun(sessionId: String?, error: BetaManagedAgentsDeploymentRun.Error? = null) {
        val run: BetaManagedAgentsDeploymentRun = mock {
            on { id() } doReturn "run_1"
            on { sessionId() } doReturn Optional.ofNullable(sessionId)
            on { error() } doReturn Optional.ofNullable(error)
        }
        whenever(deployments.run(any<DeploymentRunParams>())).thenReturn(run)
    }

    @Test
    fun `running a deployment resolves it by name and returns the session it started`() {
        givenRun(sessionId)

        val launch = agentClient.runDeployment("memory-consolidation-splitting")

        assertThat(launch).isEqualTo(DeploymentLaunch("run_1", sessionId))
        val params = argumentCaptor<DeploymentRunParams>().apply { verify(deployments).run(capture()) }.firstValue
        assertThat(params.deploymentId()).contains("dep_memory-consolidation-splitting")
    }

    @Test
    fun `a deployment run that could not start a session fails`() {
        givenRun(sessionId = null, error = mock())

        assertThatThrownBy { agentClient.runDeployment("memory-consolidation-splitting") }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("memory-consolidation-splitting")
            .hasMessageContaining("run_1")
    }

    @Test
    fun `a deployment run with neither session nor error is returned without a session`() {
        givenRun(sessionId = null)

        assertThat(agentClient.runDeployment("memory-consolidation-extraction")).isEqualTo(DeploymentLaunch("run_1", null))
    }

    // ---- getSessionSnapshot -------------------------------------------------

    private fun givenSession(status: BetaManagedAgentsSession.Status, agentName: String = "bank-statement-splitting") {
        val agent: BetaManagedAgentsSessionAgent = mock { on { name() } doReturn agentName }
        val session: BetaManagedAgentsSession = mock {
            on { status() } doReturn status
            on { agent() } doReturn agent
        }
        whenever(sessions.retrieve(any<SessionRetrieveParams>())).thenReturn(session)
    }

    /** [newestFirst] as the events API returns them for `order=desc`. */
    private fun givenEvents(vararg newestFirst: BetaManagedAgentsSessionEvent) {
        val pager: AutoPager<BetaManagedAgentsSessionEvent> = mock { on { iterator() } doReturn newestFirst.toList().iterator() }
        val page: EventListPage = mock { on { autoPager() } doReturn pager }
        whenever(events.list(eq(sessionId), any<EventListParams>())).thenReturn(page)
    }

    private fun idle(endTurn: Boolean = true): BetaManagedAgentsSessionEvent {
        val reason: BetaManagedAgentsSessionStatusIdleEvent.StopReason = mock {
            on { isEndTurn() } doReturn endTurn
            on { isRetriesExhausted() } doReturn !endTurn
        }
        val idle: BetaManagedAgentsSessionStatusIdleEvent = mock { on { stopReason() } doReturn reason }
        return mock {
            on { isSessionStatusIdle() } doReturn true
            on { asSessionStatusIdle() } doReturn idle
        }
    }

    private fun agentMessage(vararg texts: String): BetaManagedAgentsSessionEvent {
        val blocks = texts.map { text -> mock<BetaManagedAgentsTextBlock> { on { text() } doReturn text } }
        val message: BetaManagedAgentsAgentMessageEvent = mock { on { content() } doReturn blocks }
        return mock {
            on { isAgentMessage() } doReturn true
            on { asAgentMessage() } doReturn message
        }
    }

    private fun sessionError(): BetaManagedAgentsSessionEvent {
        val error: BetaManagedAgentsSessionErrorEvent = mock { on { error() } doReturn mock() }
        return mock {
            on { isSessionError() } doReturn true
            on { asSessionError() } doReturn error
        }
    }

    @Test
    fun `a running session is reported without reading its events`() {
        givenSession(BetaManagedAgentsSession.Status.RUNNING)

        val snapshot = agentClient.getSessionSnapshot(sessionId)

        assertThat(snapshot).isEqualTo(SessionSnapshot(SessionStatus.RUNNING, "bank-statement-splitting"))
        verify(events, never()).list(any<String>(), any<EventListParams>())
    }

    @Test
    fun `a rescheduling session counts as running`() {
        givenSession(BetaManagedAgentsSession.Status.RESCHEDULING)

        assertThat(agentClient.getSessionSnapshot(sessionId).status).isEqualTo(SessionStatus.RUNNING)
    }

    @Test
    fun `the agent comes from the session itself, so deployment-started sessions resolve too`() {
        givenSession(BetaManagedAgentsSession.Status.RUNNING, agentName = "memory-consolidation")

        assertThat(agentClient.getSessionSnapshot(sessionId).agent).isEqualTo("memory-consolidation")
    }

    @Test
    fun `an idle session reports its stop reason and final message, ignoring errors it recovered from`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idle(), agentMessage("{\"checks\":", " []}"), sessionError(), agentMessage("earlier"))

        val snapshot = agentClient.getSessionSnapshot(sessionId)

        assertThat(snapshot.status).isEqualTo(SessionStatus.IDLE)
        assertThat(snapshot.agent).isEqualTo("bank-statement-splitting")
        assertThat(snapshot.stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(snapshot.finalMessage).isEqualTo("{\"checks\": []}")
        assertThat(snapshot.error).isNull()
    }

    @Test
    fun `an error after the final message is reported`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idle(endTurn = false), sessionError(), agentMessage("partial"))

        val snapshot = agentClient.getSessionSnapshot(sessionId)

        assertThat(snapshot.stopReason).isEqualTo(StopReason.RETRIES_EXHAUSTED)
        assertThat(snapshot.error).isNotNull()
        assertThat(snapshot.finalMessage).isEqualTo("partial")
    }
}
