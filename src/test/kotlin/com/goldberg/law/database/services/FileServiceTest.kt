package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.exception.DuplicateEntityException
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.entity.EntityValues
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class FileServiceTest : DatabaseTest() {

    private val clientService = ClientService(db)
    private val fileService = FileService(db)

    // Shared upstream fixture — recreated before each test since the base class clears all rows.
    // insertFile is under test here so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID

    @BeforeEach
    fun createClient() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list summary, then delete`() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val fileId = fileService.insertFile(inputFile, UUID.randomUUID(), UUID.randomUUID())

            // loadFile — NOTE: has a bug in its join condition.
            // FilesTable.leftJoin(ClientsTable, { FilesTable.id }, { ClientsTable.id })
            // should be { FilesTable.clientId }. Because file_id ≠ client_id the join never
            // matches, causing Client.fromRow to throw. This test documents expected behavior
            // and will fail until the join is corrected.
            val loadedFile = fileService.loadFile(fileId)
            assertThat(loadedFile.fileId).isEqualTo(fileId)
            assertThat(loadedFile.clientId).isEqualTo(clientId)
            assertThat(loadedFile)
                .usingRecursiveComparison()
                .ignoringFields("info.fileId", "info.uploadedAt", "client.clientId", "client.createdAt")
                .isEqualTo(inputFile)

            // listFileSummaries — uses a correct join; verify all metadata fields
            val summaries = fileService.listFileSummaries(clientId)
            assertThat(summaries).hasSize(1)
            val summary = summaries.single()
            assertThat(summary.fileId).isEqualTo(fileId)
            assertThat(summary.inputFile)
                .usingRecursiveComparison()
                .ignoringFields("info.fileId", "info.uploadedAt", "client.clientId", "client.createdAt")
                .isEqualTo(inputFile)
            assertThat(summary.numChecks).isEqualTo(0)
            assertThat(summary.numStatements).isEqualTo(0)
            assertThat(summary.numTransactions).isEqualTo(0)
            assertThat(summary.numAnalyzed).isEqualTo(0)

            // Delete
            fileService.deleteInputFileWithData(fileId)

            // loadFile after delete throws
            assertThatThrownBy { fileService.loadFile(fileId) }
                .isInstanceOf(FileNotFoundException::class.java)

            // listFileSummaries after delete is empty
            assertThat(fileService.listFileSummaries(clientId)).isEmpty()
        }
    }

    @Nested
    inner class InsertFile {

        @Test
        fun `same client, filename, and request token is idempotent and returns the same UUID`() {
            val token = UUID.randomUUID()
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))

            val firstId = fileService.insertFile(inputFile, UUID.randomUUID(), token)
            val secondId = fileService.insertFile(inputFile, UUID.randomUUID(), token)

            assertThat(secondId).isEqualTo(firstId)
        }

        @Test
        fun `duplicate filename with different token throws DuplicateEntityException`() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            fileService.insertFile(inputFile, UUID.randomUUID(), UUID.randomUUID())

            assertThatThrownBy {
                fileService.insertFile(inputFile, UUID.randomUUID(), UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
        }
    }

    @Nested
    inner class LoadFilesToProcess {

        @Test
        fun `empty set returns empty Triple`() {
            val (inputFiles, classifications, classifiedItems) = fileService.loadFilesToProcess(emptySet())

            assertThat(inputFiles).isEmpty()
            assertThat(classifications).isEmpty()
            assertThat(classifiedItems).isEmpty()
        }

        @Test
        fun `unknown fileId throws EntityNotFoundException with message`() {
            assertThatThrownBy {
                fileService.loadFilesToProcess(setOf(UUID.randomUUID()))
            }.isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining("have not been uploaded")
        }

        @Test
        fun `file with no classification is returned in inputFiles with all fields`() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val fileId = fileService.insertFile(inputFile, UUID.randomUUID(), UUID.randomUUID())

            val (inputFiles, classifications, classifiedItems) = fileService.loadFilesToProcess(setOf(fileId))

            assertThat(classifications).isEmpty()
            assertThat(classifiedItems).isEmpty()
            assertThat(inputFiles).hasSize(1)
            val returnedFile = inputFiles.single()
            assertThat(returnedFile.fileId).isEqualTo(fileId)
            assertThat(returnedFile)
                .usingRecursiveComparison()
                .ignoringFields("info.fileId", "info.uploadedAt", "client.clientId", "client.createdAt")
                .isEqualTo(inputFile)
        }

        @Test
        fun `multiple fileIds all returned in inputFiles`() {
            val inputFile1 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file1.pdf"),
            )
            val inputFile2 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file2.pdf"),
            )
            val fileId1 = fileService.insertFile(inputFile1, UUID.randomUUID(), UUID.randomUUID())
            val fileId2 = fileService.insertFile(inputFile2, UUID.randomUUID(), UUID.randomUUID())

            val (inputFiles, _, _) = fileService.loadFilesToProcess(setOf(fileId1, fileId2))

            assertThat(inputFiles).hasSize(2)
            assertThat(inputFiles.map { it.fileId }).containsExactlyInAnyOrder(fileId1, fileId2)
            assertThat(inputFiles.map { it.fileName }).containsExactlyInAnyOrder("file1.pdf", "file2.pdf")
        }
    }

    @Nested
    inner class LoadFileSummary {

        @Test
        fun `unknown ID throws FileNotFoundException`() {
            assertThatThrownBy { fileService.loadFileSummary(UUID.randomUUID()) }
                .isInstanceOf(FileNotFoundException::class.java)
        }
    }

    @Nested
    inner class ListFileSummaries {

        @Test
        fun `does not return files belonging to other clients`() {
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())
            val inputFileA = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file-a.pdf"),
            )
            val inputFileB = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = otherClientId),
                info = EntityValues.newInputFileInfo(fileName = "file-b.pdf"),
            )
            fileService.insertFile(inputFileA, UUID.randomUUID(), UUID.randomUUID())
            fileService.insertFile(inputFileB, UUID.randomUUID(), UUID.randomUUID())

            val summaries = fileService.listFileSummaries(clientId)

            assertThat(summaries).hasSize(1)
            assertThat(summaries.single().fileName).isEqualTo("file-a.pdf")
            assertThat(summaries.single().clientId).isEqualTo(clientId)
        }
    }
}
