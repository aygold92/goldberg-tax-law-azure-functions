package com.goldberg.law.agent

import com.anthropic.client.AnthropicClient
import com.anthropic.core.JsonValue
import com.anthropic.core.MultipartField
import com.anthropic.models.beta.AnthropicBeta
import com.anthropic.models.beta.deployments.DeploymentRunParams
import com.anthropic.models.beta.files.FileListParams
import com.anthropic.models.beta.files.FileUploadParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsFileResourceParams
import com.anthropic.models.beta.sessions.BetaManagedAgentsMemoryStoreResourceParam
import com.anthropic.models.beta.sessions.BetaManagedAgentsSession
import com.anthropic.models.beta.sessions.SessionCreateParams
import com.anthropic.models.beta.sessions.SessionDeleteParams
import com.anthropic.models.beta.sessions.SessionRetrieveParams
import com.anthropic.models.beta.sessions.events.BetaManagedAgentsUserMessageEventParams
import com.anthropic.models.beta.sessions.events.EventListParams
import com.anthropic.models.beta.sessions.events.EventSendParams
import com.google.inject.Inject
import com.google.inject.Singleton
import io.github.oshai.kotlinlogging.KotlinLogging
import java.io.InputStream

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
     */
    fun startSession(
        agent: ManagedAgent,
        anthropicFileId: String,
        promptValues: Map<String, Any>,
        title: String,
        metadata: Map<String, String> = emptyMap(),
    ): String {
        val template = UserPromptTemplate.load(agent)

        val params = SessionCreateParams.builder()
            .addBeta(beta)
            .agent(resolver.agentId(agent))
            .environmentId(resolver.environmentId())
            .title(title)
            .metadata(
                SessionCreateParams.Metadata.builder()
                    .putAllAdditionalProperties(
                        (metadata + (AGENT_METADATA_KEY to agent.name)).mapValues { JsonValue.from(it.value) }
                    )
                    .build()
            )
            .addResource(
                BetaManagedAgentsFileResourceParams.builder()
                    .type(BetaManagedAgentsFileResourceParams.Type.FILE)
                    .fileId(anthropicFileId)
                    .mountPath(ManagedAgent.BUNDLE_MOUNT_PATH)
                    .build()
            )
        agent.memoryStore?.let { store ->
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

        logger.info { "Started ${agent.agentName} session $sessionId on $anthropicFileId" }
        return sessionId
    }

    /**
     * Fires a deployment once. A run takes no input — the deployment config carries the agent, environment,
     * memory store and kickoff message — so all there is to hand back is the run and the session it started.
     */
    fun runDeployment(deploymentName: String): DeploymentLaunch {
        val run = client.beta().deployments().run(
            DeploymentRunParams.builder().addBeta(beta).deploymentId(resolver.deploymentId(deploymentName)).build()
        )
        run.error().orElse(null)?.let {
            throw IllegalStateException("Deployment $deploymentName could not start a session (run ${run.id()}): $it")
        }
        val sessionId = run.sessionId().orElse(null)
        if (sessionId == null) {
            logger.warn { "Deployment $deploymentName run ${run.id()} reported neither a session nor an error" }
        } else {
            logger.info { "Ran deployment $deploymentName: run ${run.id()}, session $sessionId" }
        }
        return DeploymentLaunch(run.id(), sessionId)
    }

    /**
     * The session's status and, once it has stopped, how: the latest idle stop reason, the final
     * `agent.message`, and any error raised after it. Errors before the final message were recovered from.
     */
    fun getSessionSnapshot(sessionId: String): SessionSnapshot {
        val session = client.beta().sessions().retrieve(
            SessionRetrieveParams.builder().addBeta(beta).sessionId(sessionId).build()
        )
        // The session's own agent snapshot, so deployment-started sessions (which carry no metadata) resolve too
        val agent = session.agent().name()
        val status = when (session.status()) {
            BetaManagedAgentsSession.Status.IDLE -> SessionStatus.IDLE
            BetaManagedAgentsSession.Status.TERMINATED -> SessionStatus.TERMINATED
            else -> SessionStatus.RUNNING
        }
        if (status == SessionStatus.RUNNING) return SessionSnapshot(status, agent)

        var stopReason: StopReason? = null
        var error: String? = null
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
                        else -> StopReason.OTHER
                    }
                }
                event.isSessionError() && error == null -> error = event.asSessionError().error().toString()
                event.isAgentMessage() -> {
                    finalMessage = event.asAgentMessage().content().joinToString("") { it.text() }
                    break
                }
            }
        }
        return SessionSnapshot(status, agent, stopReason, error, finalMessage)
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

    companion object {
        const val SESSION_ID = "SESSION_ID"
        const val AGENT_METADATA_KEY = "agent"
    }
}

enum class SessionStatus { RUNNING, IDLE, TERMINATED }

enum class StopReason { END_TURN, REQUIRES_ACTION, RETRIES_EXHAUSTED, OTHER }

data class SessionSnapshot(
    val status: SessionStatus,
    /** The session's agent, by its `managed-agents/agents/` name. */
    val agent: String,
    val stopReason: StopReason? = null,
    val error: String? = null,
    val finalMessage: String? = null,
)

/** [sessionId] is null only if the platform reported neither a session nor an error for the run. */
data class DeploymentLaunch(val runId: String, val sessionId: String?)
