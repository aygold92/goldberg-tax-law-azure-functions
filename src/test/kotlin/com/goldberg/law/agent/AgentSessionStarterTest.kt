package com.goldberg.law.agent

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.InputFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.reset
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever
import java.util.UUID

class AgentSessionStarterTest : DatabaseTest() {

    private val launcher: AgentSessionLauncher = mock()
    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val classificationService = ClassificationService(db)
    private val starter = AgentSessionStarter(launcher, fileService, classificationService)

    private lateinit var fileId: UUID

    @BeforeEach
    fun setupFile() {
        // PER_CLASS lifecycle: rows and mock interactions would otherwise carry over between tests
        db.txnSafe { ClientsTable.deleteAll() }
        reset(launcher)
        val clientId = clientService.insertClient("client-${UUID.randomUUID()}", UUID.randomUUID())
        fileId = fileService.insertFile(
            EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file-${UUID.randomUUID()}.pdf", numPages = 12),
            ),
            UUID.randomUUID(),
        )
    }

    private fun classify(pages: Set<Int>, type: String): UUID = classificationService.insertClassifications(
        EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(EntityValues.newClassifiedPages(pages, type)))
    ).single().classificationId

    @Nested
    inner class Splitter {

        @Test
        fun `starting the splitter records the session and the uploaded file id`() {
            whenever(launcher.startSplitter(any())).thenReturn(SplitterLaunch("sess_1", "file_abc"))

            val start = starter.startSplitter(fileId)

            assertThat(start.started).isTrue()
            assertThat(start.sessionId).isEqualTo("sess_1")
            assertThat(start.anthropicFileId).isEqualTo("file_abc")
            val file = fileService.loadFile(fileId)
            assertThat(file.splitterSessionId).isEqualTo("sess_1")
            assertThat(file.anthropicFileId).isEqualTo("file_abc")
        }

        @Test
        fun `a file that has already been split is not split again`() {
            fileService.updateSplitterSession(fileId, "sess_1", "file_abc")

            val start = starter.startSplitter(fileId)

            assertThat(start.started).isFalse()
            assertThat(start.sessionId).isEqualTo("sess_1")
            assertThat(start.anthropicFileId).isEqualTo("file_abc")
            verify(launcher, never()).startSplitter(any())
        }

        @Test
        fun `override splits the file again and records the new session`() {
            fileService.updateSplitterSession(fileId, "sess_1", "file_abc")
            whenever(launcher.startSplitter(any<InputFile>())).thenReturn(SplitterLaunch("sess_2", "file_def"))

            val start = starter.startSplitter(fileId, override = true)

            assertThat(start.started).isTrue()
            assertThat(start.sessionId).isEqualTo("sess_2")
            assertThat(fileService.loadFile(fileId).splitterSessionId).isEqualTo("sess_2")
        }
    }

    @Nested
    inner class StatementExtraction {

        @BeforeEach
        fun uploadFile() {
            fileService.updateSplitterSession(fileId, "sess_split", "file_abc")
        }

        @Test
        fun `page range, bank id and the check pages inside the range come from the classifications`() {
            val statementId = classify(setOf(1, 2, 3, 4), "chase_cc")
            classify(setOf(3, 4, 9), DocumentType.CheckTypes.CHECKS)
            whenever(launcher.startStatementExtraction(any(), any(), any(), any(), any(), any())).thenReturn("sess_x")

            val start = starter.startStatementExtraction(statementId)

            assertThat(start.started).isTrue()
            // Page 9 is a check page outside this statement's range
            verify(launcher).startStatementExtraction(
                eq("file_abc"), any(), eq(1), eq(4), eq("chase_cc"), eq(listOf(3, 4)),
            )
            assertThat(classificationService.loadClassification(statementId).info.extractionSessionId).isEqualTo("sess_x")
        }

        @Test
        fun `a classification that has already been extracted is not extracted again`() {
            val statementId = classify(setOf(1, 2), "chase_cc")
            classificationService.updateExtractionSession(statementId, "sess_old")

            val start = starter.startStatementExtraction(statementId)

            assertThat(start.started).isFalse()
            assertThat(start.sessionId).isEqualTo("sess_old")
            verify(launcher, never()).startStatementExtraction(any(), any(), any(), any(), any(), any())
        }

        @Test
        fun `override extracts again and records the new session`() {
            val statementId = classify(setOf(1, 2), "chase_cc")
            classificationService.updateExtractionSession(statementId, "sess_old")
            whenever(launcher.startStatementExtraction(any(), any(), any(), any(), any(), any())).thenReturn("sess_new")

            val start = starter.startStatementExtraction(statementId, override = true)

            assertThat(start.started).isTrue()
            assertThat(classificationService.loadClassification(statementId).info.extractionSessionId).isEqualTo("sess_new")
        }
    }

    @Nested
    inner class CheckExtraction {

        @Test
        fun `the classification's pages are the pages to read`() {
            fileService.updateSplitterSession(fileId, "sess_split", "file_abc")
            val checkId = classify(setOf(5, 4), DocumentType.CheckTypes.CHECKS)
            whenever(launcher.startCheckExtraction(any(), any())).thenReturn("sess_c")

            starter.startCheckExtraction(checkId)

            verify(launcher).startCheckExtraction("file_abc", listOf(4, 5))
            assertThat(classificationService.loadClassification(checkId).info.extractionSessionId).isEqualTo("sess_c")
        }
    }

    @Test
    fun `extraction before the file has been uploaded to Anthropic fails`() {
        val statementId = classify(setOf(1, 2), "chase_cc")

        assertThatThrownBy { starter.startStatementExtraction(statementId) }
            .isInstanceOf(IllegalStateException::class.java)
            .hasMessageContaining("run the splitter first")
    }
}
