package com.goldberg.law.agent

import com.azure.core.util.BinaryData
import com.goldberg.law.agent.model.output.CheckExtractionOutput
import com.goldberg.law.agent.model.output.SplitterOutput
import com.goldberg.law.agent.model.output.StatementExtractionOutput
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.entity.InputFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.argumentCaptor
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
    private val checkJson = """{"checks": [{"page": 5, "check_no": 1042, "amt": 12.50}]}"""

    private fun givenSnapshot(snapshot: SessionSnapshot) {
        whenever(agentClient.getSessionSnapshot(sessionId)).thenReturn(snapshot)
    }

    private fun givenResultFile(text: String) {
        whenever(agentClient.downloadSessionOutput(sessionId, "result.json")).thenReturn(text)
    }

    private fun givenNoResultFile() {
        whenever(agentClient.downloadSessionOutput(sessionId, "result.json"))
            .thenThrow(IllegalStateException("Session $sessionId has no output file named result.json"))
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
        whenever(agentClient.startSession(eq(ManagedAgent.SPLITTER), eq("file_1"), any(), any(), any(), any())).thenReturn(sessionId)

        val launch = launcher.startSplitter(inputFile)

        assertThat(launch).isEqualTo(SplitterLaunch(sessionId, "file_1"))
        verify(agentClient).startSession(
            eq(ManagedAgent.SPLITTER),
            eq("file_1"),
            eq(mapOf("FILE_NAME" to "bundle-a.pdf")),
            any(),
            eq(SessionBudget.dollars(5)),
            eq(mapOf("fileId" to fileId.toString(), "clientId" to clientId.toString())),
        )
    }

    @Test
    fun `statement extraction passes every prompt value, check pages as a list`() {
        whenever(agentClient.startSession(any(), any(), any(), any(), any(), any())).thenReturn(sessionId)

        val result = launcher.startStatementExtraction("file_1", "bundle-a.pdf", 3, 9, "bank_of_america", emptyList())

        assertThat(result).isEqualTo(sessionId)
        verify(agentClient).startSession(
            eq(ManagedAgent.STATEMENT_EXTRACTION),
            eq("file_1"),
            eq(mapOf("START" to 3, "END" to 9, "BANK_ID" to "bank_of_america", "CHECK_PAGES" to emptyList<Int>(), "FILE_NAME" to "bundle-a.pdf")),
            any(),
            eq(SessionBudget.dollars(5)),
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
        verify(agentClient, never()).startSession(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `check extraction passes the pages as a list`() {
        whenever(agentClient.startSession(any(), any(), any(), any(), any(), any())).thenReturn(sessionId)

        launcher.startCheckExtraction("file_1", listOf(5, 8))

        verify(agentClient).startSession(eq(ManagedAgent.CHECK_EXTRACTION), eq("file_1"), eq(mapOf("PAGES" to listOf(5, 8))), any(), eq(SessionBudget.dollars(5)), any())
    }

    @Test
    fun `check extraction requires at least one page`() {
        assertThatThrownBy { launcher.startCheckExtraction("file_1", emptyList()) }
            .isInstanceOf(IllegalArgumentException::class.java)
        verify(agentClient, never()).startSession(any(), any(), any(), any(), any(), any())
    }

    private fun givenFolder(store: String, bankId: String, vararg names: String) {
        whenever(agentClient.listMemories(store, "/$bankId/"))
            .thenReturn(names.map { StoredMemory("/$bankId/$it", 100, 1L) })
    }

    @Test
    fun `memory consolidation starts one session on the chosen store for the one bank folder`() {
        givenFolder("extraction-notes", "bank_of_america", "main.md", "sesn_1.md")
        whenever(agentClient.startMemorySession(any(), any(), any(), any(), any(), any())).thenReturn(sessionId)

        val started = launcher.startMemoryConsolidation(MemoryConsolidation.EXTRACTION, "bank_of_america")

        assertThat(started).isEqualTo(sessionId)
        val values = argumentCaptor<Map<String, Any>>().apply {
            verify(agentClient).startMemorySession(eq(ManagedAgent.MEMORY_CONSOLIDATION), eq("extraction-notes"), capture(), any(), eq(SessionBudget.dollars(2)), any())
        }.firstValue
        assertThat(values).containsEntry("BANK_ID", "bank_of_america")
        // The format the extraction agent wrote to, not the splitter's
        assertThat(values["FORMAT"] as String).contains("# Extraction notes format")
    }

    @Test
    fun `each store's consolidation carries its own writer's format`() {
        givenFolder("bank-patterns", "chase_cc", "sesn_1.md")
        whenever(agentClient.startMemorySession(any(), any(), any(), any(), any(), any())).thenReturn(sessionId)

        launcher.startMemoryConsolidation(MemoryConsolidation.SPLITTING, "chase_cc")

        val values = argumentCaptor<Map<String, Any>>().apply {
            verify(agentClient).startMemorySession(any(), eq("bank-patterns"), capture(), any(), any(), any())
        }.firstValue
        assertThat(values["FORMAT"] as String).contains("# Pattern file format")
    }

    @Test
    fun `memory consolidation rejects a bank id that isn't a folder name`() {
        listOf("", "Bank_Of_America", "../bank_of_america", "chase_cc/main.md", "chase cc").forEach { bankId ->
            assertThatThrownBy { launcher.startMemoryConsolidation(MemoryConsolidation.SPLITTING, bankId) }
                .isInstanceOf(IllegalArgumentException::class.java)
        }
        verify(agentClient, never()).startMemorySession(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `memory consolidation starts nothing for a folder that doesn't exist`() {
        givenFolder("bank-patterns", "chase_cc")

        assertThatThrownBy { launcher.startMemoryConsolidation(MemoryConsolidation.SPLITTING, "chase_cc") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("No 'chase_cc' folder")
        verify(agentClient, never()).startMemorySession(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `memory consolidation starts nothing for a folder with no session files`() {
        // A consolidated folder: main.md, plus a stray file that isn't a session's
        givenFolder("bank-patterns", "chase_cc", "main.md", "notes.md")

        assertThatThrownBy { launcher.startMemoryConsolidation(MemoryConsolidation.SPLITTING, "chase_cc") }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("no session files")
        verify(agentClient, never()).startMemorySession(any(), any(), any(), any(), any(), any())
    }

    @Test
    fun `listing memory groups the whole store into bank folders`() {
        whenever(agentClient.listMemories("extraction-notes")).thenReturn(
            listOf(StoredMemory("/chase_cc/main.md", 900, 1L), StoredMemory("/chase_cc/sesn_1.md", 100, 2L)),
        )

        val listing = launcher.listMemory(MemoryConsolidation.EXTRACTION)

        assertThat(listing.memory).isEqualTo(MemoryConsolidation.EXTRACTION)
        assertThat(listing.folder("chase_cc")!!.sessionFileCount).isEqualTo(1)
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
    fun `a finished session reads its result from the session's result file`() {
        val message = "Read 1 check off page 5."
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, message))
        givenResultFile(checkJson)

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat(result.output).isInstanceOf(CheckExtractionOutput::class.java)
        assertThat((result.output as CheckExtractionOutput).checks.single().checkNo).isEqualTo(1042)
        // the message is carried for logging, never parsed
        assertThat(result.rawOutput).isEqualTo(message)
    }

    @Test
    fun `every agent reads the same result file`() {
        // a filename in the message is prose like the rest of it
        givenSnapshot(finished(ManagedAgent.SPLITTER, """Split into 2 statements. {"file": "boundaries.json"}"""))
        givenResultFile("""{"banks": {}, "boundaries": []}""")

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat((result.output as SplitterOutput).boundaries).isEmpty()
    }

    @Test
    fun `a session that ended without a message still has its result`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, null))
        givenResultFile("""{"bank_id": "chase", "statement_date": "2024-02-29", "accounts": []}""")

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat((result.output as StatementExtractionOutput).bankId).isEqualTo("chase")
    }

    @Test
    fun `an agent that reports nothing to extract is an agent error`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, "The pages hold no register."))
        givenResultFile("""{"error": "pages 3-9 are a cover letter"}""")

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.AGENT_ERROR)
        assertThat(result.error).isEqualTo("pages 3-9 are a cover letter")
        assertThat(result.output).isNull()
    }

    @Test
    fun `an error in the final message is not an agent error without the result file`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, """{"error": "pages 3-9 are a cover letter"}"""))
        givenNoResultFile()

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).contains("result.json")
    }

    @Test
    fun `a result file that isn't the agent's schema fails`() {
        givenSnapshot(finished(ManagedAgent.STATEMENT_EXTRACTION, "Done."))
        givenResultFile("I could not finish the extraction.")

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).contains("result.json")
    }

    @Test
    fun `a prose report is the output, with nothing to parse`() {
        val report = "Merged 3 session files into bank_of_america/main.md, recorded 1 conflict, deleted 3 files."
        givenSnapshot(finished(ManagedAgent.MEMORY_CONSOLIDATION, report))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.COMPLETED)
        assertThat(result.agent).isEqualTo(ManagedAgent.MEMORY_CONSOLIDATION)
        assertThat(result.output).isEqualTo(report)
        verify(agentClient, never()).downloadSessionOutput(any(), any())
    }

    @Test
    fun `a prose agent that ended without a message failed`() {
        givenSnapshot(finished(ManagedAgent.MEMORY_CONSOLIDATION, null))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).contains("without an agent message")
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
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, "Done.").copy(error = "billing"))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).isEqualTo("billing")
        verify(agentClient, never()).downloadSessionOutput(any(), any())
    }

    @Test
    fun `a session that wrote no result file failed, keeping the raw message`() {
        val message = "I ran out of time before writing the output."
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, message))
        givenNoResultFile()

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.FAILED)
        assertThat(result.error).contains("result.json")
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

    // ---- cancelling ---------------------------------------------------------

    @Test
    fun `cancelling reports whether there was a running session to interrupt`() {
        whenever(agentClient.interruptIfRunning(sessionId)).thenReturn(true, false)

        assertThat(launcher.cancelSession(sessionId)).isTrue()
        assertThat(launcher.cancelSession(sessionId)).isFalse()
    }

    @Test
    fun `an interrupted session is cancelled, even with a result file to read`() {
        givenSnapshot(finished(ManagedAgent.CHECK_EXTRACTION, "Extracted 1 check.").copy(interrupted = true))

        val result = launcher.fetchResult(sessionId)

        assertThat(result.status).isEqualTo(AgentSessionResult.Status.CANCELLED)
        assertThat(result.output).isNull()
        verify(agentClient, never()).downloadSessionOutput(any(), any())
    }

    @Test
    fun `a session still winding down after an interrupt is running`() {
        givenSnapshot(SessionSnapshot(SessionStatus.RUNNING, ManagedAgent.SPLITTER.agentName))

        assertThat(launcher.fetchResult(sessionId).status).isEqualTo(AgentSessionResult.Status.RUNNING)
    }
}
