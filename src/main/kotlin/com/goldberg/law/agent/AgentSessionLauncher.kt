package com.goldberg.law.agent

import com.goldberg.law.agent.model.output.AgentOutputParser
import com.goldberg.law.agent.model.output.AgentResult
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.entity.InputFile
import com.google.inject.Inject
import com.google.inject.Singleton

/**
 * Starts each agent from domain inputs and reads back its result. This is the seam the Durable
 * orchestration will call; the HTTP functions are a thin shell over it.
 */
@Singleton
class AgentSessionLauncher @Inject constructor(
    private val agentClient: AnthropicAgentClient,
    private val dataManager: AzureStorageDataManager,
) {
    /** Uploads the bundle and starts the splitter. The returned file id is reused by the extraction agents. */
    fun startSplitter(inputFile: InputFile): SplitterLaunch {
        val bytes = dataManager.loadInputPdfDocument(inputFile).toBinaryData().toBytes()
        val anthropicFileId = agentClient.uploadPdf(bytes, inputFile.fileName)
        val sessionId = agentClient.startSession(
            ManagedAgent.SPLITTER,
            anthropicFileId,
            mapOf("FILE_NAME" to inputFile.fileName),
            title = "Split ${inputFile.fileName}",
            budget = AGENT_BUDGET,
            metadata = mapOf("fileId" to inputFile.fileId.toString(), "clientId" to inputFile.clientId.toString()),
        )
        return SplitterLaunch(sessionId, anthropicFileId)
    }

    /**
     * [fullRead] tells the agent to read the whole statement instead of only what its memory points at, so a
     * section or figure the notes record as absent still gets a chance to be noticed.
     */
    fun startStatementExtraction(
        anthropicFileId: String,
        fileName: String,
        startPage: Int,
        endPage: Int,
        bankId: String,
        checkPages: List<Int>,
        fullRead: Boolean = false,
    ): String {
        require(startPage in 1..endPage) { "Invalid page range $startPage–$endPage" }
        requirePositive(checkPages)
        return agentClient.startSession(
            ManagedAgent.STATEMENT_EXTRACTION,
            anthropicFileId,
            mapOf(
                "START" to startPage,
                "END" to endPage,
                "BANK_ID" to bankId,
                "CHECK_PAGES" to checkPages,
                "FILE_NAME" to fileName,
                "FULL_READ" to if (fullRead) "yes" else "no",
            ),
            title = "Extract $fileName pages $startPage–$endPage",
            budget = AGENT_BUDGET,
            metadata = mapOf("bankId" to bankId),
        )
    }

    fun startCheckExtraction(anthropicFileId: String, pages: List<Int>): String {
        require(pages.isNotEmpty()) { "No pages given" }
        requirePositive(pages)
        return agentClient.startSession(
            ManagedAgent.CHECK_EXTRACTION,
            anthropicFileId,
            mapOf("PAGES" to pages),
            title = "Extract checks from ${pages.size} pages",
            budget = AGENT_BUDGET,
        )
    }

    /** Every folder and file in [memory]'s store, without their content. */
    fun listMemory(memory: MemoryConsolidation): MemoryStoreListing =
        MemoryStoreListing.of(memory, agentClient.listMemories(memory.memoryStore))

    /**
     * Consolidates one bank's folder of [memory]'s store, once the folder is confirmed to exist and hold session
     * files — otherwise there's nothing to consolidate and no session is started. A session per folder keeps
     * each folder's notes out of every other folder's context, which a whole-store run re-reads on every
     * request. Its session is read back with [fetchResult] like any other.
     */
    fun startMemoryConsolidation(memory: MemoryConsolidation, bankId: String): String {
        require(BANK_ID.matches(bankId)) { "Invalid bank id '$bankId': expected lowercase letters, digits and underscores" }
        val folder = MemoryStoreListing.of(memory, agentClient.listMemories(memory.memoryStore, "/$bankId/")).folder(bankId)
        require(folder != null) { "No '$bankId' folder in ${memory.memoryStore}" }
        require(folder.sessionFileCount > 0) { "The '$bankId' folder in ${memory.memoryStore} has no session files to consolidate" }

        return agentClient.startMemorySession(
            ManagedAgent.MEMORY_CONSOLIDATION,
            memory.memoryStore,
            mapOf("BANK_ID" to bankId, "FORMAT" to memory.format),
            title = "Consolidate $bankId in ${memory.memoryStore}",
            budget = CONSOLIDATION_BUDGET,
            metadata = mapOf("bankId" to bankId, "memoryStore" to memory.memoryStore),
        )
    }

    /**
     * Interrupts a running session. Returns whether it was running; the session reaches idle at its next safe
     * boundary, so [fetchResult] can still report it as running for a short while before [AgentSessionResult.Status.CANCELLED].
     */
    fun cancelSession(sessionId: String): Boolean = agentClient.interruptIfRunning(sessionId)

    fun fetchResult(sessionId: String): AgentSessionResult {
        val snapshot = agentClient.getSessionSnapshot(sessionId)
        val agent = ManagedAgent.byAgentName(snapshot.agent)
        val raw = snapshot.finalMessage

        fun result(status: AgentSessionResult.Status, output: Any? = null, error: String? = null, json: String? = null) =
            AgentSessionResult(sessionId, agent, status, output, error, raw, json)

        return when {
            snapshot.status == SessionStatus.RUNNING -> result(AgentSessionResult.Status.RUNNING)
            snapshot.status == SessionStatus.TERMINATED ->
                result(AgentSessionResult.Status.FAILED, error = "Session terminated${snapshot.error?.let { ": $it" }.orEmpty()}")
            // Checked before the stop reason: an interrupted turn ends with end_turn like a finished one
            snapshot.interrupted -> result(AgentSessionResult.Status.CANCELLED)
            snapshot.stopReason != StopReason.END_TURN ->
                result(AgentSessionResult.Status.FAILED, error = "Session stopped (${snapshot.stopReason})${snapshot.error?.let { ": $it" }.orEmpty()}")
            snapshot.error != null -> result(AgentSessionResult.Status.FAILED, error = snapshot.error)
            agent == null -> result(AgentSessionResult.Status.FAILED, error = "Session belongs to agent '${snapshot.agent}', which this app doesn't run")
            // An agent that reports in prose has nothing to parse: the report is the output
            agent.outputType == null -> raw?.let { result(AgentSessionResult.Status.COMPLETED, output = it) }
                ?: result(AgentSessionResult.Status.FAILED, error = "Session ended without an agent message")
            else -> readOutput(sessionId, agent).let { result(it.status, it.output, it.error, it.json) }
        }
    }

    /**
     * Reads a finished session's result out of [ManagedAgent.OUTPUT_FILE] — the agent's schema output, or the
     * `{"error": …}` shape it writes when it couldn't produce one. The agent's messages are never parsed.
     */
    private fun readOutput(sessionId: String, agent: ManagedAgent): Read {
        val fileText = try {
            agentClient.downloadSessionOutput(sessionId, ManagedAgent.OUTPUT_FILE)
        } catch (ex: Exception) {
            return Read(AgentSessionResult.Status.FAILED, error = "Could not read ${ManagedAgent.OUTPUT_FILE}: ${ex.message}")
        }

        val parsed = runCatching { AgentOutputParser.parse(fileText, agent.outputType!!) }
        return when (val value = parsed.getOrNull()) {
            is AgentResult.Output -> Read(AgentSessionResult.Status.COMPLETED, output = value.value, json = fileText)
            is AgentResult.Error -> Read(AgentSessionResult.Status.AGENT_ERROR, error = value.error, json = fileText)
            null -> Read(
                AgentSessionResult.Status.FAILED,
                error = "${ManagedAgent.OUTPUT_FILE} is not ${agent.agentName} output: ${parsed.exceptionOrNull()?.message}",
                json = fileText,
            )
        }
    }

    private fun requirePositive(pages: List<Int>) = require(pages.all { it > 0 }) { "Page numbers are 1-indexed: $pages" }

    private companion object {
        /** A bank id is also a memory folder name, so it's held to the snake_case the agents coin. */
        val BANK_ID = Regex("[a-z0-9_]+")

        /** What one splitter or extraction session may spend. */
        val AGENT_BUDGET = SessionBudget.dollars(5)

        /** One bank folder: the whole extraction store, all 11 folders in one session, reached $2.02. */
        val CONSOLIDATION_BUDGET = SessionBudget.dollars(2)
    }

    /** What reading the output file concluded, before it's folded into the [AgentSessionResult]. */
    private data class Read(
        val status: AgentSessionResult.Status,
        val output: Any? = null,
        val error: String? = null,
        val json: String? = null,
    )
}

data class SplitterLaunch(val sessionId: String, val anthropicFileId: String)

data class AgentSessionResult(
    val sessionId: String,
    val agent: ManagedAgent?,
    val status: Status,
    /**
     * Once [Status.COMPLETED]: the agent's schema output — one of the classes in `agent/model/output/`, read
     * from [ManagedAgent.OUTPUT_FILE] — or, for an agent that reports in prose, the report text.
     */
    val output: Any? = null,
    val error: String? = null,
    /** The agent's final message verbatim. Agents report in prose alongside their output file; this is for logs. */
    val rawOutput: String? = null,
    /**
     * [ManagedAgent.OUTPUT_FILE] verbatim, whenever it could be read — including when parsing it then failed.
     * This is what gets archived for a completed session, rather than a re-serialized [output], so the record
     * stays faithful to what the agent actually wrote.
     */
    val outputJson: String? = null,
) {
    enum class Status {
        RUNNING,
        COMPLETED,
        /** The agent finished but reported the task impossible (the schema's `{"error": …}` shape). */
        AGENT_ERROR,
        /** Interrupted before it finished; any `rawOutput` is the last thing it said, not a result. */
        CANCELLED,
        FAILED,
    }
}
