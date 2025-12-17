package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.exception.DuplicateClientTokenException
import com.goldberg.law.database.exception.DuplicateEntityException
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeAll
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

    @BeforeAll
    fun createClient() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
    }

    @BeforeEach
    fun clearFiles() {
        db.txnSafe {
            FilesTable.deleteAll()
        }
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list summary, then delete`() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            // create
            val fileId = fileService.insertFile(inputFile, UUID.randomUUID())

            // read
            val loadedFile = fileService.loadFile(fileId)
            assertThat(loadedFile).entityCompare().isEqualTo(inputFile)
            assertThat(loadedFile.fileId).isEqualTo(fileId)
            assertThat(loadedFile.clientId).isEqualTo(clientId)
            assertTimeIsDuringTest(loadedFile.info.uploadedAt)

            // list
            val summary = fileService.listFileSummaries(clientId).single()
            assertThat(summary).entityCompare().isEqualTo(EntityValues.newInputFileSummary(inputFile = inputFile))
            assertThat(summary.inputFile).isEqualTo(loadedFile)
            assertThat(summary.clientId).isEqualTo(clientId)

            // Delete
            fileService.deleteInputFile(fileId)

            // loadFile after delete throws not found
            assertThatThrownBy { fileService.loadFile(fileId) }
                .isInstanceOf(FileNotFoundException::class.java)

            // list after delete is empty
            assertThat(fileService.listFileSummaries(clientId)).isEmpty()
        }
    }

    @Nested
    inner class ClientToken {

        @Test
        fun `same client, filename, content hash, and request token is idempotent and returns the same UUID`() {
            val token = UUID.randomUUID()
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))

            val firstId = fileService.insertFile(inputFile, token)
            val secondId = fileService.insertFile(inputFile, token)

            assertThat(secondId).isEqualTo(firstId)
        }

        @Test
        fun `same entity with different token throws DuplicateEntityException`() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val fileId = fileService.insertFile(inputFile, UUID.randomUUID())

            assertThatThrownBy {
                fileService.insertFile(inputFile, UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
                .hasMessageContaining(fileId.toString())
                .hasMessageContaining(EntityValues.DEFAULT_FILENAME)
                .hasMessageNotContaining(EntityValues.DEFAULT_FILE_CONTENT_HASH.toString())
        }

        @Test
        fun `same request token but different content hash throws error`() {
            val token = UUID.randomUUID()
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val inputFile2 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(contentHash = UUID.randomUUID())
            )

            fileService.insertFile(inputFile, token)
            assertThatThrownBy {
                fileService.insertFile(inputFile2, token)
            }.isInstanceOf(DuplicateClientTokenException::class.java)
        }

        @Test
        fun `same request token but different client throws error`() {
            val otherClientId = clientService.insertClient("Test", UUID.randomUUID())
            try {
                val token = UUID.randomUUID()
                val inputFile1 = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
                val inputFile2 = EntityValues.newInputFile(client = EntityValues.newClient(clientId = otherClientId))

                fileService.insertFile(inputFile1, token)
                // TODO: if there were ever multiple users we should allow same request token for different clients
                assertThatThrownBy {
                    fileService.insertFile(inputFile2, token)
                }.isInstanceOf(DuplicateClientTokenException::class.java)
            } finally {
                clientService.deleteClient(otherClientId)
            }
        }

        @Test
        fun `same request token but different filename throws error`() {
            val token = UUID.randomUUID()
            val inputFile1 = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val inputFile2 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "other filename")
            )

            fileService.insertFile(inputFile1, token)
            assertThatThrownBy {
                fileService.insertFile(inputFile2, token)
            }.isInstanceOf(DuplicateClientTokenException::class.java)
        }
    }

    @Nested
    inner class InsertFile {
        @Test
        fun `duplicate file for different clients is fine`() {
            val otherClientName = "test"
            val otherClientId = clientService.insertClient(otherClientName, UUID.randomUUID())
            val inputFile1 = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val inputFile2 = EntityValues.newInputFile(client = EntityValues.newClient(clientId = otherClientId, clientName = otherClientName))
            val fileId1 = fileService.insertFile(inputFile1, UUID.randomUUID())
            val fileId2 = fileService.insertFile(inputFile2, UUID.randomUUID())

            val loadedFile1 = fileService.loadFile(fileId1)
            assertThat(loadedFile1).entityCompare().isEqualTo(inputFile1)
            assertThat(loadedFile1.clientId).isEqualTo(clientId)

            val loadedFile2 = fileService.loadFile(fileId2)
            assertThat(loadedFile2).entityCompare().isEqualTo(inputFile2)
            assertThat(loadedFile2.clientId).isEqualTo(otherClientId)

            assertThat(loadedFile1.info).entityCompare().isEqualTo(loadedFile2.info)
        }

        @Test
        fun `duplicate contentHash throws DuplicateEntityException with fileId but not hash or filename`() {
            val inputFile1 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
            )
            val inputFile2 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "other file name")
            )

            val fileId = fileService.insertFile(inputFile1, UUID.randomUUID())

            assertThatThrownBy {
                fileService.insertFile(inputFile2, UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
                .hasMessageContaining(fileId.toString())
                .hasMessageNotContaining(EntityValues.DEFAULT_FILE_CONTENT_HASH.toString())
                .hasMessageNotContaining(EntityValues.DEFAULT_FILENAME)
        }

        @Test
        fun `duplicate filename throws DuplicateEntityException`() {
            val inputFile1 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
            )
            val inputFile2 = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(contentHash = UUID.randomUUID())
            )

            val fileId = fileService.insertFile(inputFile1, UUID.randomUUID())

            assertThatThrownBy {
                fileService.insertFile(inputFile2, UUID.randomUUID())
            }.isInstanceOf(DuplicateEntityException::class.java)
                .hasMessageContaining(fileId.toString())
                .hasMessageContaining(EntityValues.DEFAULT_FILENAME)
                .hasMessageNotContaining(EntityValues.DEFAULT_FILE_CONTENT_HASH.toString())
        }
    }

    @Nested
    inner class LoadFilesToProcess {
        lateinit var fileId: UUID
        val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
        @BeforeEach
        fun insertFile() {
            val inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            // create
            fileId = fileService.insertFile(inputFile, UUID.randomUUID())
        }

        @Test
        fun `empty set returns empty Triple`() {
            val (inputFiles, classifications, classifiedItems) = fileService.loadFilesToProcess(emptySet())

            assertThat(inputFiles).isEmpty()
            assertThat(classifications).isEmpty()
            assertThat(classifiedItems).isEmpty()
        }

        @Test
        fun `unknown fileId throws EntityNotFoundException with message`() {
            val randomId = UUID.randomUUID()
            assertThatThrownBy {
                fileService.loadFilesToProcess(setOf(randomId))
            }.isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining(randomId.toString())
        }

        @Test
        fun `known and unknown fileId throws EntityNotFoundException`() {
            val randomId = UUID.randomUUID()
            assertThatThrownBy {
                fileService.loadFilesToProcess(setOf(fileId, randomId))
            }.isInstanceOf(EntityNotFoundException::class.java)
                .hasMessageContaining(randomId.toString())
                .hasMessageNotContaining(fileId.toString())
        }

        @Test
        fun `file with no classification is returned in inputFiles with all fields`() {
            val (inputFiles, classifications, classifiedItems) = fileService.loadFilesToProcess(setOf(fileId))

            assertThat(classifications).isEmpty()
            assertThat(classifiedItems).isEmpty()
            assertThat(inputFiles).hasSize(1)
            val returnedFile = inputFiles.single()
            assertThat(returnedFile.fileId).isEqualTo(fileId)
            assertThat(returnedFile).entityCompare().isEqualTo(inputFile)
        }

        @Test
        fun `multiple fileIds all returned in inputFiles`() {
            val otherInputFile = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(fileName = "file2.pdf", contentHash = UUID.randomUUID()),
            )
            val fileId2 = fileService.insertFile(otherInputFile, UUID.randomUUID())

            val (inputFiles, _, _) = fileService.loadFilesToProcess(setOf(fileId, fileId2))

            assertThat(inputFiles).hasSize(2)
            assertThat(inputFiles.map { it.fileId }).containsExactlyInAnyOrder(fileId, fileId2)
            assertThat(inputFiles.find { it.fileId == fileId }).entityCompare().isEqualTo(inputFile)
            assertThat(inputFiles.find { it.fileId == fileId2 }).entityCompare().isEqualTo(otherInputFile)
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

        private val classificationService = ClassificationService(db)
        private val transactionService = TransactionService(db)
        private val statementService = StatementService(transactionService, BankStatementVerifier(TransactionVerifier()), db)
        private val checkService = CheckService(db)

        @Test
        fun `does not return files belonging to other clients`() {
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())
            val inputFileA = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = clientId),
                info = EntityValues.newInputFileInfo(),
            )
            val inputFileB = EntityValues.newInputFile(
                client = EntityValues.newClient(clientId = otherClientId),
                info = EntityValues.newInputFileInfo(fileName = "file-b.pdf"),
            )
            fileService.insertFile(inputFileA, UUID.randomUUID())
            fileService.insertFile(inputFileB, UUID.randomUUID())

            val summaries = fileService.listFileSummaries(clientId)

            assertThat(summaries).hasSize(1)
            assertThat(summaries.single()).entityCompare().isEqualTo(EntityValues.newInputFileSummary())
        }

        @Test
        fun `summary counts 2 statements, 3 checks, and 5 transactions across 4 classifications`() {
            val fileInfo = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
            val fileId = fileService.insertFile(
                fileInfo,
                UUID.randomUUID(),
            )

            // Insert 4 classifications with distinct page sets (uniqueness constraint on MD5(pages))
            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(
                    fileId = fileId,
                    classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(1)),  // → 1 statement, 2 transactions
                        EntityValues.newClassifiedPages(pages = setOf(2)),  // → 1 statement, 3 transactions
                        EntityValues.newClassifiedPages(pages = setOf(3)),  // → 3 checks
                        EntityValues.newClassifiedPages(pages = setOf(4)),  // → empty, no model update
                    )
                )
            )
            val clsf1 = classificationService.loadClassification(infos[0].classificationId)
            val clsf2 = classificationService.loadClassification(infos[1].classificationId)
            val clsf3 = classificationService.loadClassification(infos[2].classificationId)
            // infos[3] intentionally left without a model update

            classificationService.updateModelLocation(clsf1.classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            classificationService.updateModelLocation(clsf2.classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            classificationService.updateModelLocation(clsf3.classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)

            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = clsf1,
                    transactions = listOf(
                        EntityValues.newTransactionDetails(description = "txn 1"),
                        EntityValues.newTransactionDetails(description = "txn 2"),
                    ),
                )
            )
            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = clsf2,
                    transactions = listOf(
                        EntityValues.newTransactionDetails(description = "txn 3"),
                        EntityValues.newTransactionDetails(description = "txn 4"),
                        EntityValues.newTransactionDetails(description = "txn 5"),
                    ),
                )
            )

            checkService.insertCheck(clsf3, EntityValues.newCheckDetails(checkNumber = 1001))
            checkService.insertCheck(clsf3, EntityValues.newCheckDetails(checkNumber = 1002))
            checkService.insertCheck(clsf3, EntityValues.newCheckDetails(checkNumber = 1003))

            val summary = fileService.listFileSummaries(clientId).single()

            assertThat(summary).entityCompare().isEqualTo(EntityValues.newInputFileSummary(
                inputFile = fileInfo,
                numChecks = 3,
                numStatements = 2,
                numTransactions = 5,
                numAnalyzed = 3,
                numDocuments = 4,
            ))
        }
    }
}
