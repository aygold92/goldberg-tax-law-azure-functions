package com.goldberg.law.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.MultipartField
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.files.FileListParams
import com.anthropic.models.beta.files.FileUploadParams
import com.anthropic.models.beta.memorystores.memories.BetaManagedAgentsMemoryView
import com.anthropic.models.beta.memorystores.memories.MemoryListParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsFileResourceParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsMemoryStoreResourceParam
import com.anthropic.models.beta.sessions.BetaManagedAgentsSession
import com.anthropic.models.beta.sessions.SessionCreateParams
import com.anthropic.models.beta.sessions.SessionDeleteParams
import com.anthropic.models.beta.sessions.SessionRetrieveParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsSessionStatusIdleEvent
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserInterruptEventParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserMessageEventParams
import com.anthropic.models.beta.sessions.events.EventListParams
import com.anthropic.models.beta.sessions.events.EventSendParams
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream
import kotlin.jvm.optionals.getOrNull

/**
 * The app's only touchpoint with the Anthropic SDK for running agents. Speaks in agent names and plain
 * values so callers never build SDK params; resource ids come from [ManagedAgentResourceResolver].
 */
@Singleton
class AnthropicAgentClient @Inject constructor(
    private val client: AnthropicClient,
    private val resolver: ManagedAgentResourceResolver,
) {
    private val logger = KotlinLogging.logger {}
    private val beta = AnthropicBeta.MANAGED_AGENTS_2026_04_01

    /** Memory-store calls take their own beta header; the API rejects one sent with both. */
    private val memoryBeta = AnthropicBeta.AGENT_MEMORY_2026_07_22

    fun uploadPdf(bytes: ByteArray, fileName: String): String = client.beta().files().upload(
        FileUploadParams.builder()
            .file(
                MultipartField.builder<InputStream>()
                    .value(bytes.inputStream())
                    .filename(fileName)
                    .contentType("application/pdf")
                    .build()
            )
            .build()
    ).id().also { logger.info { "Uploaded $fileName to Anthropic as $it" } }

    /**
     * Starts [agent] on the bundle [anthropicFileId] and returns the session id.
     *
     * Two calls, because the prompt needs the session id (`{SESSION_ID}` names the agent's memory files) and
     * that only exists once the session does. If the kickoff message fails, the session is deleted rather
     * than left idle with nothing to do.
     *
     * [metadata] ties the session back to this app's records (visible in the Console, and on the session for
     * later lookups); nothing here depends on it — a session's agent is read from the session itself.
     */
    fun startSession(
        agent: ManagedAgent,
        anthropicFileId: String,
        promptValues: Map<String, Any>,
        title: String,
        budget: SessionBudget,
        metadata: Map<String, String> = emptyMap(),
    ): String = createAndPrompt(agent, anthropicFileId, agent.memoryStore, promptValues, title, budget, metadata)

    /** Starts [agent] on [memoryStore] alone, with no bundle mounted — for an agent that works on memory, not a PDF. */
    fun startMemorySession(
        agent: ManagedAgent,
        memoryStore: String,
        promptValues: Map<String, Any>,
        title: String,
        budget: SessionBudget,
        metadata: Map<String, String> = emptyMap(),
    ): String = createAndPrompt(agent, anthropicFileId = null, memoryStore, promptValues, title, budget, metadata)

    private fun createAndPrompt(
        agent: ManagedAgent,
        anthropicFileId: String?,
        memoryStore: String?,
        promptValues: Map<String, Any>,
        title: String,
        budget: SessionBudget,
        metadata: Map<String, String>,
    ): String {
        val template = UserPromptTemplate.load(agent)

        val params = SessionCreateParams.builder()
            .addBeta(beta)
            .agent(resolver.agentId(agent))
            .environmentId(resolver.environmentId())
            .title(title)
            // The SDK has no typed budget yet, so the API's own shape goes on the body verbatim
            .putAdditionalBodyProperty("budget", JsonValue.from(budget.toApi()))
        anthropicFileId?.let { fileId ->
            params.addResource(
                BetaManagedAgentsFileResourceParams.builder()
                    .type(BetaManagedAgentsFileResourceParams.Type.FILE)
                    .fileId(fileId)
                    .mountPath(ManagedAgent.BUNDLE_MOUNT_PATH)
                    .build()
            )
        }
        if (metadata.isNotEmpty()) {
            params.metadata(
                SessionCreateParams.Metadata.builder()
                    .putAllAdditionalProperties(metadata.mapValues { JsonValue.from(it.value) })
                    .build()
            )
        }
        memoryStore?.let { store ->
            params.addResource(
                BetaManagedAgentsMemoryStoreResourceParam.builder()
                    .type(BetaManagedAgentsMemoryStoreResourceParam.Type.MEMORY_STORE)
                    .memoryStoreId(resolver.memoryStoreId(store))
                    .access(BetaManagedAgentsMemoryStoreResourceParam.Access.READ_WRITE)
                    .build()
            )
        }
        val sessionId = client.beta().sessions().create(params.build()).id()

        try {
            // Only agents that write memory files take a session id (check-extraction is stateless)
            val prompt = template.render(
                if (SESSION_ID in template.placeholders) promptValues + (SESSION_ID to sessionId) else promptValues
            )
            client.beta().sessions().events().send(
                EventSendParams.builder()
                    .addBeta(beta)
                    .sessionId(sessionId)
                    .addEvent(
                        BetaManagedAgentsUserMessageEventParams.builder()
                            .type(BetaManagedAgentsUserMessageEventParams.Type.USER_MESSAGE)
                            .addTextContent(prompt)
                            .build()
                    )
                    .build()
            )
        } catch (ex: Exception) {
            runCatching {
                client.beta().sessions().delete(SessionDeleteParams.builder().addBeta(beta).sessionId(sessionId).build())
            }.onFailure { ex.addSuppressed(it) }
            throw ex
        }

        logger.info { "Started ${agent.agentName} session $sessionId on ${anthropicFileId ?: memoryStore}" }
        return sessionId
    }

    /**
     * Every memory under [pathPrefix] in [memoryStore], without content. [pathPrefix] must end with `/`; the
     * listing covers its whole subtree.
     */
    fun listMemories(memoryStore: String, pathPrefix: String = "/"): List<StoredMemory> {
        require(pathPrefix.startsWith("/") && pathPrefix.endsWith("/")) { "A memory path prefix starts and ends with '/': $pathPrefix" }
        return client.beta().memoryStores().memories()
            .list(
                resolver.memoryStoreId(memoryStore),
                MemoryListParams.builder()
                    .addBeta(memoryBeta)
                    .pathPrefix(pathPrefix)
                    .view(BetaManagedAgentsMemoryView.BASIC)
                    .build(),
            )
            .autoPager().asSequence()
            // A whole-subtree listing holds memories only; prefixes appear only for a depth-limited one
            .mapNotNull { it.memory().getOrNull() }
            .map { StoredMemory(it.path(), it.contentSizeBytes(), it.updatedAt().toInstant().toEpochMilli()) }
            .toList()
    }

    /**
     * Stops a running session by sending `user.interrupt`, which jumps the queue and idles the session at its
     * next safe boundary. Returns false, sending nothing, when the session isn't running: an interrupt against
     * an idle session does nothing, but it would still land in the history and make a finished session read
     * as cancelled in [getSessionSnapshot].
     */
    fun interruptIfRunning(sessionId: String): Boolean {
        if (retrieve(sessionId).toSessionStatus() != SessionStatus.RUNNING) {
            logger.info { "Session $sessionId is not running; nothing to interrupt" }
            return false
        }
        client.beta().sessions().events().send(
            EventSendParams.builder()
                .addBeta(beta)
                .sessionId(sessionId)
                .addEvent(
                    BetaManagedAgentsUserInterruptEventParams.builder()
                        .type(BetaManagedAgentsUserInterruptEventParams.Type.USER_INTERRUPT)
                        .build()
                )
                .build()
        )
        logger.info { "Interrupted session $sessionId" }
        return true
    }

    /**
     * The session's status and, once it has stopped, how: the latest idle stop reason, the final
     * `agent.message`, and anything that happened after it — an error, or an interrupt. Errors and interrupts
     * before the final message were recovered from.
     */
    fun getSessionSnapshot(sessionId: String): SessionSnapshot {
        val session = retrieve(sessionId)
        // The session's own agent snapshot, so deployment-started sessions (which carry no metadata) resolve too
        val agent = session.agent().name()
        val status = session.toSessionStatus()
        if (status == SessionStatus.RUNNING) return SessionSnapshot(status, agent)

        var stopReason: StopReason? = null
        var error: String? = null
        var interrupted = false
        var finalMessage: String? = null
        val newestFirst = client.beta().sessions().events()
            .list(sessionId, EventListParams.builder().addBeta(beta).order(EventListParams.Order.DESC).build())
            .autoPager().asSequence()
        for (event in newestFirst) {
            when {
                event.isSessionStatusIdle() && stopReason == null -> stopReason = event.asSessionStatusIdle().stopReason().let {
                    when {
                        it.isEndTurn() -> StopReason.END_TURN
                        it.isRequiresAction() -> StopReason.REQUIRES_ACTION
                        it.isRetriesExhausted() -> StopReason.RETRIES_EXHAUSTED
                        // No typed variant in the SDK yet, but a budgeted session can stop this way, so name it
                        it.rawType() == "budget_reached" -> StopReason.BUDGET_REACHED
                        else -> StopReason.OTHER
                    }
                }
                event.isSessionError() && error == null -> error = event.asSessionError().error().toString()
                // An interrupted turn still ends with end_turn, so the interrupt event is the only sign of it
                event.isUserInterrupt() -> interrupted = true
                event.isAgentMessage() -> {
                    finalMessage = event.asAgentMessage().content().joinToString("") { it.text() }
                    break
                }
            }
        }
        return SessionSnapshot(status, agent, stopReason, error, finalMessage, interrupted)
    }

    /** Reads a file the agent wrote to `/mnt/session/outputs/`, which the Files API captures per session. */
    fun downloadSessionOutput(sessionId: String, fileName: String): String {
        val file = client.beta().files()
            .list(FileListParams.builder().addBeta(beta).scopeId(sessionId).build())
            .autoPager().asSequence()
            .firstOrNull { it.filename() == fileName }
            ?: throw IllegalStateException("Session $sessionId has no output file named $fileName")
        return client.beta().files().download(file.id()).use { it.body().readBytes().decodeToString() }
    }

    private fun retrieve(sessionId: String): BetaManagedAgentsSession = client.beta().sessions().retrieve(
        SessionRetrieveParams.builder().addBeta(beta).sessionId(sessionId).build()
    )

    /** The `type` of a stop reason the SDK has no class for; null for the ones it does model. */
    private fun BetaManagedAgentsSessionStatusIdleEvent.StopReason.rawType(): String? =
        _json().getOrNull()?.runCatching { convert(Map::class.java)?.get("type") as? String }?.getOrNull()

    private fun BetaManagedAgentsSession.toSessionStatus() = when (status()) {
        BetaManagedAgentsSession.Status.IDLE -> SessionStatus.IDLE
        BetaManagedAgentsSession.Status.TERMINATED -> SessionStatus.TERMINATED
        else -> SessionStatus.RUNNING
    }

    companion object {
        const val SESSION_ID = "SESSION_ID"
    }
}

enum class SessionStatus { RUNNING, IDLE, TERMINATED }

enum class StopReason { END_TURN, REQUIRES_ACTION, RETRIES_EXHAUSTED, BUDGET_REACHED, OTHER }

data class SessionSnapshot(
    val status: SessionStatus,
    /** The session's agent, by its `managed-agents/agents/` name. */
    val agent: String,
    val stopReason: StopReason? = null,
    val error: String? = null,
    val finalMessage: String? = null,
    /** An interrupt arrived after the final message: the session was cancelled, not finished. */
    val interrupted: Boolean = false,
)

/**
 * Hard ceiling on what one session may spend, priced at public list rates. A session that reaches it stops
 * issuing model requests and goes idle with stop reason `budget_reached`; the request that crosses the cap
 * finishes, so the final cost can land a fraction past it.
 */
data class SessionBudget(val cents: Int) {
    init {
        require(cents > 0) { "A session budget must be positive: $cents cents" }
    }

    /** The API takes whole US cents as a string, so no float rounding is applied. */
    fun toApi() = mapOf("type" to "limit", "max_list_cost" to mapOf("amount" to cents.toString(), "currency" to "USD"))

    companion object {
        fun dollars(dollars: Int) = SessionBudget(dollars * 100)
    }
}
