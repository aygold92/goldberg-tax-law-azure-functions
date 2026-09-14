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
            metadata = mapOf("fileId" to inputFile.fileId.toString(), "clientId" to inputFile.clientId.toString()),
        )
        return SplitterLaunch(sessionId, anthropicFileId)
    }

    fun startStatementExtraction(
        anthropicFileId: String,
        fileName: String,
        startPage: Int,
        endPage: Int,
        bankId: String,
        checkPages: List<Int>,
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
            ),
            title = "Extract $fileName pages $startPage–$endPage",
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
        )
    }

    /** Runs the consolidation deployment for one memory store. Its session is read back with [fetchResult] like any other. */
    fun startMemoryConsolidation(memory: MemoryConsolidation): DeploymentLaunch =
        agentClient.runDeployment(memory.deploymentName)

    fun fetchResult(sessionId: String): AgentSessionResult {
        val snapshot = agentClient.getSessionSnapshot(sessionId)
        val agent = ManagedAgent.byAgentName(snapshot.agent)
        val raw = snapshot.finalMessage

        fun result(status: AgentSessionResult.Status, output: Any? = null, outputFile: String? = null, error: String? = null) =
            AgentSessionResult(sessionId, agent, status, output, outputFile, error, raw)

        return when {
            snapshot.status == SessionStatus.RUNNING -> result(AgentSessionResult.Status.RUNNING)
            snapshot.status == SessionStatus.TERMINATED ->
                result(AgentSessionResult.Status.FAILED, error = "Session terminated${snapshot.error?.let { ": $it" }.orEmpty()}")
            snapshot.stopReason != StopReason.END_TURN ->
                result(AgentSessionResult.Status.FAILED, error = "Session stopped (${snapshot.stopReason})${snapshot.error?.let { ": $it" }.orEmpty()}")
            snapshot.error != null -> result(AgentSessionResult.Status.FAILED, error = snapshot.error)
            raw == null -> result(AgentSessionResult.Status.FAILED, error = "Session ended without an agent message")
            agent == null -> result(AgentSessionResult.Status.FAILED, error = "Session belongs to agent '${snapshot.agent}', which this app doesn't run")
            // An agent that reports in prose has nothing to parse: the report is the output
            agent.outputType == null -> result(AgentSessionResult.Status.COMPLETED, output = raw)
            else -> try {
                when (val parsed = AgentOutputParser.parse(raw, agent.outputType)) {
                    is AgentResult.Output -> result(AgentSessionResult.Status.COMPLETED, output = parsed.value)
                    is AgentResult.Error -> result(AgentSessionResult.Status.AGENT_ERROR, error = parsed.error)
                    is AgentResult.OutputFile -> {
                        val fileText = agentClient.downloadSessionOutput(sessionId, parsed.file)
                        when (val fromFile = AgentOutputParser.parse(fileText, agent.outputType)) {
                            is AgentResult.Output ->
                                result(AgentSessionResult.Status.COMPLETED, output = fromFile.value, outputFile = parsed.file)
                            else -> result(
                                AgentSessionResult.Status.FAILED,
                                outputFile = parsed.file,
                                error = "Output file ${parsed.file} did not hold the agent's output: $fromFile",
                            )
                        }
                    }
                }
            } catch (ex: Exception) {
                result(AgentSessionResult.Status.FAILED, error = "Could not read agent output: ${ex.message}")
            }
        }
    }

    private fun requirePositive(pages: List<Int>) = require(pages.all { it > 0 }) { "Page numbers are 1-indexed: $pages" }
}

data class SplitterLaunch(val sessionId: String, val anthropicFileId: String)

data class AgentSessionResult(
    val sessionId: String,
    val agent: ManagedAgent?,
    val status: Status,
    /**
     * Once [Status.COMPLETED]: the agent's schema output — one of the classes in `agent/model/output/` — or,
     * for an agent that reports in prose, the report text.
     */
    val output: Any? = null,
    /** Set when the output was too large to return inline and was read from this session output file. */
    val outputFile: String? = null,
    val error: String? = null,
    /** The agent's final message verbatim, so a result that fails to parse can still be inspected. */
    val rawOutput: String? = null,
) {
    enum class Status {
        RUNNING,
        COMPLETED,
        /** The agent finished but reported the task impossible (the schema's `{"error": …}` shape). */
        AGENT_ERROR,
        FAILED,
    }
}
