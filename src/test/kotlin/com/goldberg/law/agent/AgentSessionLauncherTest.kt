package com.goldberg.law.agent

import com.azure.core.util.BinaryData
import com.goldberg.law.agent.model.output.CheckExtractionOutput
import com.goldberg.law.agent.model.output.StatementExtractionOutput
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.entity.InputFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AgentSessionLauncherTest {

    private val agentClient: AnthropicAgentClient = mock()
    private val dataManager: AzureStorageDataManager = mock()
    private val launcher = AgentSessionLauncher(agentClient, dataManager)

    private val sessionId = "sesn_1"
    private val checkJson = """{"checks": [{"page": 5, "check_number": 1042, "amount": 12.50}]}"""

    private fun givenSnapshot(snapshot: SessionSnapshot) {
        whenever(agentClient.getSessionSnapshot(sessionId)).thenReturn(snapshot)
    }

    private fun finished(agentName: String, message: String?) =
        SessionSnapshot(SessionStatus.IDLE, agentName, StopReason.END_TURN, finalMessage = message)

    private fun finished(agent: ManagedAgent, message: String?) = finished(agent.agentName, message)

    // ---- starting sessions --------------------------------------------------

    @Test
    fun `splitter uploads the input file's bytes, then starts on the uploaded file`() {
        val fileId = UUID.randomUUID()
        val clientId = UUID.randomUUID()
        val inputFile: InputFile = mock {
            on { this.fileName } doReturn "bundle-a.pdf"
            on { this.fileId } doReturn fileId
            on { this.clientId } doReturn clientId
        }
        val bytes = byteArrayOf(1, 2, 3)
        val pdf: PdfDocument = mock { on { toBinaryData() } doReturn BinaryData.fromBytes(bytes) }
        whenever(dataManager.loadInputPdfDocument(inputFile)).thenReturn(pdf)
        whenever(agentClient.uploadPdf(bytes, "bundle-a.pdf")).thenReturn("file_1")
        whenever(agentClient.startSession(eq(ManagedAgent.SPLITTER), eq("file_1"), any(), any(), any())).thenReturn(sessionId)

        val launch = launcher.startSplitter(inputFile)

        assertThat(launch).isEqualTo(SplitterLaunch(sessionId, "file_1"))
        verify(agentClient).startSession(
            eq(ManagedAgent.SPLITTER),
            eq("file_1"),
            eq(mapOf("FILE_NAME" to "bundle-a.pdf")),
            any(),
            eq(mapOf("fileId" to fileId.toString(), "clientId" to clientId.toString())),
        )
    }

    @Test
    fun `statement extraction passes every prompt value, check pages as a list`() {
        whenever(agentClient.startSession(any(), any(), any(), any(), any())).thenReturn(sessionId)

        val result = launcher.startStatementExtraction("file_1", "bundle-a.pdf", 3, 9, "bank_of_america", emptyList())

        assertThat(result).isEqualTo(sessionId)
        verify(agentClient).startSession(
            eq(ManagedAgent.STATEMENT_EXTRACTION),
            eq("file_1"),
            eq(mapOf("START" to 3, "END" to 9, "BANK_ID" to "bank_of_america", "CHECK_PAGES" to emptyList<Int>(), "FILE_NAME" to "bundle-a.pdf")),
            any(),
            any(),
        )
    }

    @Test
    fun `statement extraction rejects an inverted or zero-based page range`() {
        assertThatThrownBy { launcher.startStatementExtraction("file_1", "a.pdf", 9, 3, "bank", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { launcher.startStatementExtraction("file_1", "a.pdf", 0, 3, "bank", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { launcher.startStatementExtraction("file_1", "a.pdf", 1, 3, "bank", listOf(0)) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(agentClient, never()).startSession(any(), any(), any(), any(), any())
    }

    @Test
    fun `check extraction passes the pages as a list`() {
        whenever(agentClient.startSession(any(), any(), any(), any(), any())).thenReturn(sessionId)

        launcher.startCheckExtraction("file_1", listOf(5, 8))

        verify(agentClient).startSession(eq(ManagedAgent.CHECK_EXTRACTION), eq("file_1"), eq(mapOf("PAGES" to listOf(5, 8))), any(), any())
    }

    @Test
    fun `check extraction requires at least one page`() {
        assertThatThrownBy { launcher.startCheckExtraction("file_1", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(agentClient, never()).startSession(any(), any(), any(), any(), any())
    }

    @Test
    fun `memory consolidation runs the deployment for the chosen store`() {
        whenever(agentClient.runDeployment("memory-consolidation-extraction")).thenReturn(DeploymentLaunch("run_1", sessionId))

        val launch = launcher.startMemoryConsolidation(MemoryConsolidation.EXTRACTION)

        assertThat(launch).isEqualTo(DeploymentLaunch("run_1", sessionId))
    }

    // ---- fetching results ---------------------------------------------------

    @Test
    fun `a running session has no output yet`() {
        givenSnapshot(SessionSnapshot(SessionStatus.RUNNING, ManagedAgent.CHECK_EXTRACTION.agentName))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.RUNNING)
        assertThat(result.agent).isEqualTo(ManagedAgent.CHECK_EXTRACTION)
        assertThat(result.output).isNull()
    }

    @Test
    fun `a finished session parses its final message as that agent's output`() {
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, checkJson))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat(result.output).isInstanceOf(CheckExtractionOutput::class.java)
        assertThat((result.output as CheckExtractionOutput).checks.single().checkNumber).isEqualTo(1042)
        assertThat(result.outputFile).isNull()
        assertThat(result.rawOutput).isEqualTo(checkJson)
    }

    @Test
    fun `a prose report is the output, with nothing to parse`() {
        val report = "Merged 3 session files into bank_of_america/main.md, recorded 1 conflict, deleted 3 files."
        givenSnapshot(finished(ManagedAgent.MEMORY_CONSOLIDATION, report))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat(result.agent).isEqualTo(ManagedAgent.MEMORY_CONSOLIDATION)
        assertThat(result.output).isEqualTo(report)
        assertThat(result.rawOutput).isEqualTo(report)
    }

    @Test
    fun `an output too large to return inline is read from the session's output file`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, """{"file": "statement.json"}"""))
        whenever(agentClient.downloadSessionOutput(sessionId, "statement.json"))
            .thenReturn("""{"bank_id": "chase", "statement_date": "2024-02-29", "accounts": []}""")

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat(result.outputFile).isEqualTo("statement.json")
        assertThat((result.output as StatementExtractionOutput).bankId).isEqualTo("chase")
    }

    @Test
    fun `an agent that reports nothing to extract is an agent error`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, """{"error": "pages 3-9 are a cover letter"}"""))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.AGENT_ERROR)
        assertThat(result.error).isEqualTo("pages 3-9 are a cover letter")
        assertThat(result.output).isNull()
    }

    @Test
    fun `a session that exhausted its retries failed`() {
        givenSnapshot(SessionSnapshot(SessionStatus.IDLE, ManagedAgent.SPLITTER.agentName, StopReason.RETRIES_EXHAUSTED, error = "model overloaded"))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).contains("RETRIES_EXHAUSTED").contains("model overloaded")
    }

    @Test
    fun `a terminated session failed`() {
        givenSnapshot(SessionSnapshot(SessionStatus.TERMINATED, ManagedAgent.SPLITTER.agentName))

        assertThat(launcher.fetchResult(sessionId).status).isEqualTo(AgentSessionResult.Status.FAILED)
    }

    @Test
    fun `an error after the final message fails the session even at end of turn`() {
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, checkJson).copy(error = "billing"))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).isEqualTo("billing")
    }

    @Test
    fun `unparseable output fails but keeps the raw message`() {
        val message = "Here is the JSON you asked for: {"
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, message))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.rawOutput).isEqualTo(message)
    }

    @Test
    fun `a session for an agent this app doesn't run can't be parsed`() {
        givenSnapshot(finished("some-other-agent", checkJson))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.agent).isNull()
        assertThat(result.error).contains("some-other-agent")
        assertThat(result.rawOutput).isEqualTo(checkJson)
    }
}
