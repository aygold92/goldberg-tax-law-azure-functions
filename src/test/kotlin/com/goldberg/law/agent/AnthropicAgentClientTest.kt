package com.goldberg.law.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.AutoPager
import com.anthropic.core.JsonValue
import com.anthropic.models.beta.memorystores.memories.BetaManagedAgentsMemory
import com.anthropic.models.beta.memorystores.memories.BetaManagedAgentsMemoryListItem
import com.anthropic.models.beta.memorystores.memories.BetaManagedAgentsMemoryView
import com.anthropic.models.beta.memorystores.memories.MemoryListPage
import com.anthropic.models.beta.memorystores.memories.MemoryListParams
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
import com.anthropic.services.blocking.beta.MemoryStoreService
import com.anthropic.services.blocking.beta.FileService
import com.anthropic.services.blocking.beta.SessionService
import com.anthropic.services.blocking.beta.memorystores.MemoryService
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
import java.time.OffsetDateTime
import java.util.Optional

class AnthropicAgentClientTest {

    private val sessionId = "sesn_1"

    private val events: EventService = mock()
    private val sessions: SessionService = mock { on { events() } doReturn events }
    private val files: FileService = mock()
    private val memories: MemoryService = mock()
    private val memoryStores: MemoryStoreService = mock { on { memories() } doReturn memories }
    private val beta: BetaService = mock {
        on { sessions() } doReturn sessions
        on { files() } doReturn files
        on { memoryStores() } doReturn memoryStores
    }
    private val client: AnthropicClient = mock { on { beta() } doReturn beta }
    private val resolver: ManagedAgentResourceResolver = mock {
        on { agentId(any()) } doAnswer { "agent_" + it.getArgument<ManagedAgent>(0).name }
        on { environmentId() } doReturn "env_1"
        on { memoryStoreId(any()) } doAnswer { "store_" + it.getArgument<String>(0) }
    }
    private val agentClient = AnthropicAgentClient(client, resolver)
    private val budget = SessionBudget.dollars(5)

    private fun givenSessionCreated() {
        val session: BetaManagedAgentsSession = mock { on { id() } doReturn sessionId }
        whenever(sessions.create(any<SessionCreateParams>())).thenReturn(session)
    }

    private fun createdParams(): SessionCreateParams =
        argumentCaptor<SessionCreateParams>().apply { verify(sessions).create(capture()) }.firstValue

    private fun sentEvents(): EventSendParams =
        argumentCaptor<EventSendParams>().apply { verify(events).send(capture()) }.firstValue
            .also { assertThat(it.sessionId()).contains(sessionId) }

    private fun sentPrompt(): String = sentEvents().events().single().asUserMessage().content().single().asText().text()

    // ---- startSession -------------------------------------------------------

    @Test
    fun `a splitter session mounts the bundle and the bank-patterns store read-write`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "Split a.pdf", budget, mapOf("fileId" to "f1"))

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
    fun `a memory session mounts only the named store, read-write, and no bundle`() {
        givenSessionCreated()

        agentClient.startMemorySession(
            ManagedAgent.MEMORY_CONSOLIDATION,
            "extraction-notes",
            mapOf("BANK_ID" to "chase_cc", "FORMAT" to "# Extraction notes format"),
            "Consolidate chase_cc",
            budget,
        )

        val params = createdParams()
        assertThat(params.agent().asString()).isEqualTo("agent_MEMORY_CONSOLIDATION")
        val resources = params.resources().get()
        assertThat(resources).noneMatch { it.isFile() }
        val store = resources.single().asMemoryStore()
        assertThat(store.memoryStoreId()).isEqualTo("store_extraction-notes")
        assertThat(store.access()).contains(BetaManagedAgentsMemoryStoreResourceParam.Access.READ_WRITE)
        assertThat(sentPrompt()).contains("`chase_cc`").contains("# Extraction notes format")
    }

    @Test
    fun `session metadata is exactly the caller's metadata`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t", budget, mapOf("fileId" to "f1"))

        assertThat(createdParams().metadata().get()._additionalProperties())
            .containsExactlyEntriesOf(mapOf("fileId" to JsonValue.from("f1")))
    }

    @Test
    fun `a session without caller metadata sends none`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5)), "t", budget)

        assertThat(createdParams().metadata()).isEmpty()
    }

    @Test
    fun `a session is capped at the budget it's given`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5)), "t", SessionBudget.dollars(2))

        assertThat(createdParams()._additionalBodyProperties()["budget"]).isEqualTo(
            JsonValue.from(
                mapOf(
                    "type" to "limit",
                    // Whole US cents as a string: $2.00
                    "max_list_cost" to mapOf("amount" to "200", "currency" to "USD"),
                )
            )
        )
    }

    @Test
    fun `a budget must be positive`() {
        assertThatThrownBy { SessionBudget(0) }.isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `an extraction session mounts the extraction-notes store`() {
        givenSessionCreated()

        agentClient.startSession(
            ManagedAgent.STATEMENT_EXTRACTION, "file_1",
            mapOf("START" to 1, "END" to 2, "BANK_ID" to "b", "CHECK_PAGES" to emptyList<Int>(), "FILE_NAME" to "a.pdf", "FULL_READ" to "no"),
            "t",
            budget,
        )

        val store = createdParams().resources().get().single { it.isMemoryStore() }.asMemoryStore()
        assertThat(store.memoryStoreId()).isEqualTo("store_extraction-notes")
    }

    @Test
    fun `a check extraction session mounts only the bundle`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5, 6)), "t", budget)

        assertThat(createdParams().resources().get()).singleElement().matches { it.isFile() }
    }

    @Test
    fun `the kickoff prompt carries the id of the session it was sent to`() {
        givenSessionCreated()

        val result = agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t", budget)

        assertThat(result).isEqualTo(sessionId)
        assertThat(sentPrompt()).contains(sessionId).contains("a.pdf")
    }

    @Test
    fun `a prompt without a session id placeholder is sent without one`() {
        givenSessionCreated()

        agentClient.startSession(ManagedAgent.CHECK_EXTRACTION, "file_1", mapOf("PAGES" to listOf(5, 6)), "t", budget)

        assertThat(sentPrompt()).contains("pages [5, 6]").doesNotContain(sessionId)
    }

    @Test
    fun `a failed kickoff deletes the session rather than leaving it idle`() {
        givenSessionCreated()
        whenever(events.send(any<EventSendParams>())).thenThrow(RuntimeException("boom"))

        assertThatThrownBy {
            agentClient.startSession(ManagedAgent.SPLITTER, "file_1", mapOf("FILE_NAME" to "a.pdf"), "t", budget)
        }.hasMessage("boom")

        val deleted = argumentCaptor<SessionDeleteParams>().apply { verify(sessions).delete(capture()) }.firstValue
        assertThat(deleted.sessionId()).contains(sessionId)
    }

    @Test
    fun `a prompt that doesn't match its values deletes the session and sends nothing`() {
        givenSessionCreated()

        assertThatThrownBy {
            agentClient.startSession(ManagedAgent.SPLITTER, "file_1", emptyMap(), "t", budget)
        }.isInstanceOf(IllegalArgumentException::class.java).hasMessageContaining("FILE_NAME")

        verify(sessions).delete(any<SessionDeleteParams>())
        verify(events, never()).send(any<EventSendParams>())
    }

    // ---- listMemories -------------------------------------------------------

    private fun memory(path: String, size: Int = 10): BetaManagedAgentsMemoryListItem {
        val memory: BetaManagedAgentsMemory = mock {
            on { path() } doReturn path
            on { contentSizeBytes() } doReturn size
            on { updatedAt() } doReturn OffsetDateTime.parse("2026-09-26T12:00:00Z")
        }
        return mock {
            on { memory() } doReturn Optional.of(memory)
        }
    }

    private fun givenMemories(vararg items: BetaManagedAgentsMemoryListItem) {
        val pager: AutoPager<BetaManagedAgentsMemoryListItem> = mock { on { iterator() } doReturn items.toList().iterator() }
        val page: MemoryListPage = mock { on { autoPager() } doReturn pager }
        whenever(memories.list(any<String>(), any<MemoryListParams>())).thenReturn(page)
    }

    private fun listedParams(): Pair<String, MemoryListParams> {
        val store = argumentCaptor<String>()
        val params = argumentCaptor<MemoryListParams>()
        verify(memories).list(store.capture(), params.capture())
        return store.firstValue to params.firstValue
    }

    @Test
    fun `listing memories resolves the store by name and asks for paths without content`() {
        givenMemories(memory("/chase_cc/main.json", 1200), memory("/chase_cc/sesn_1.json", 300))

        val listed = agentClient.listMemories("bank-patterns")

        assertThat(listed).containsExactly(
            StoredMemory("/chase_cc/main.json", 1200, 1790424000000),
            StoredMemory("/chase_cc/sesn_1.json", 300, 1790424000000),
        )
        val (store, params) = listedParams()
        assertThat(store).isEqualTo("store_bank-patterns")
        assertThat(params.view()).contains(BetaManagedAgentsMemoryView.BASIC)
        assertThat(params.pathPrefix()).contains("/")
        // The whole subtree, so no depth limit
        assertThat(params.depth()).isEmpty()
    }

    @Test
    fun `listing memories can be scoped to one folder`() {
        givenMemories()

        agentClient.listMemories("bank-patterns", "/chase_cc/")

        assertThat(listedParams().second.pathPrefix()).contains("/chase_cc/")
    }

    @Test
    fun `a memory path prefix must start and end with a slash`() {
        assertThatThrownBy { agentClient.listMemories("bank-patterns", "chase_cc") }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(memories, never()).list(any<String>(), any<MemoryListParams>())
    }

    // ---- interruptIfRunning -------------------------------------------------

    @Test
    fun `interrupting a running session sends a user interrupt`() {
        givenSession(BetaManagedAgentsSession.Status.RUNNING)

        assertThat(agentClient.interruptIfRunning(sessionId)).isTrue()

        assertThat(sentEvents().events().single().isUserInterrupt()).isTrue()
    }

    @Test
    fun `a rescheduling session is running, so it is interrupted`() {
        givenSession(BetaManagedAgentsSession.Status.RESCHEDULING)

        assertThat(agentClient.interruptIfRunning(sessionId)).isTrue()
    }

    @Test
    fun `an idle or terminated session is left alone, so a finished session never reads as cancelled`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        assertThat(agentClient.interruptIfRunning(sessionId)).isFalse()

        givenSession(BetaManagedAgentsSession.Status.TERMINATED)
        assertThat(agentClient.interruptIfRunning(sessionId)).isFalse()

        verify(events, never()).send(any<EventSendParams>())
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

    /** An idle event whose stop reason the SDK has no class for, as the budget cap produces. */
    private fun idleUnmodelled(type: String): BetaManagedAgentsSessionEvent {
        val reason: BetaManagedAgentsSessionStatusIdleEvent.StopReason = mock {
            on { _json() } doReturn Optional.of(JsonValue.from(mapOf("type" to type)))
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

    private fun userInterrupt(): BetaManagedAgentsSessionEvent = mock { on { isUserInterrupt() } doReturn true }

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
        assertThat(snapshot.interrupted).isFalse()
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

    @Test
    fun `a session stopped by its budget reports that stop reason`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idleUnmodelled("budget_reached"), agentMessage("partial"))

        assertThat(agentClient.getSessionSnapshot(sessionId).stopReason).isEqualTo(StopReason.BUDGET_REACHED)
    }

    @Test
    fun `a stop reason this app does not know is reported as other`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idleUnmodelled("something_new"), agentMessage("partial"))

        assertThat(agentClient.getSessionSnapshot(sessionId).stopReason).isEqualTo(StopReason.OTHER)
    }

    @Test
    fun `an interrupt after the final message marks the session interrupted, though it ended with end_turn`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idle(), userInterrupt(), agentMessage("Reading page 4 now"))

        val snapshot = agentClient.getSessionSnapshot(sessionId)

        assertThat(snapshot.interrupted).isTrue()
        assertThat(snapshot.stopReason).isEqualTo(StopReason.END_TURN)
        assertThat(snapshot.finalMessage).isEqualTo("Reading page 4 now")
    }

    @Test
    fun `an interrupt the session later moved past does not mark it interrupted`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idle(), agentMessage("{\"checks\": []}"), userInterrupt(), agentMessage("earlier"))

        assertThat(agentClient.getSessionSnapshot(sessionId).interrupted).isFalse()
    }

    @Test
    fun `an interrupt before the agent said anything still marks the session interrupted`() {
        givenSession(BetaManagedAgentsSession.Status.IDLE)
        givenEvents(idle(), userInterrupt())

        val snapshot = agentClient.getSessionSnapshot(sessionId)

        assertThat(snapshot.interrupted).isTrue()
        assertThat(snapshot.finalMessage).isNull()
    }
}
