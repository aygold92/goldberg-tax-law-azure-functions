package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ClassificationServiceTest : DatabaseTest() {

    // Use real verifier instances — BankStatementVerifier is a final Kotlin class
    // and no inline mock-maker is configured in this project.
    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val statementService = StatementService(bankStatementVerifier, db)
    private val classificationService = ClassificationService(statementService, db)

    private val clientService = ClientService(db)
    private val fileService = FileService(db)

    // Shared upstream fixtures — recreated before each test.
    // insertClassifications is under test here, so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID
    private lateinit var fileId: UUID

    @BeforeEach
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list by fileId, update model location, update classification, then delete`() {
            // Insert
            val classifiedFile = EntityValues.newClassifiedFile(fileId = fileId)
            val infos = classificationService.insertClassifications(classifiedFile)
            assertThat(infos).hasSize(1)
            val info = infos.single()
            assertThat(info.pages).isEqualTo(EntityValues.DEFAULT_PAGES)
            assertThat(info.classificationType).isEqualTo(EntityValues.DEFAULT_CLASSIFICATION_TYPE)
            assertThat(info.modelLocation).isNull()
            val classificationId = info.classificationId

            // Load by ID — verify all key fields
            val loaded = classificationService.loadClassification(classificationId)
            assertThat(loaded.classificationId).isEqualTo(classificationId)
            assertThat(loaded.fileId).isEqualTo(fileId)
            assertThat(loaded.clientId).isEqualTo(clientId)
            assertThat(loaded.pages).isEqualTo(EntityValues.DEFAULT_PAGES)
            assertThat(loaded.classificationType).isEqualTo(EntityValues.DEFAULT_CLASSIFICATION_TYPE)
            assertThat(loaded.info.modelLocation).isNull()

            // List by fileId — verify appears with correct fields
            val listedByFile = classificationService.loadClassifications(fileId)
            assertThat(listedByFile).hasSize(1)
            val listedClassification = listedByFile.single()
            assertThat(listedClassification.classificationId).isEqualTo(classificationId)
            assertThat(listedClassification.fileId).isEqualTo(fileId)
            assertThat(listedClassification.clientId).isEqualTo(clientId)
            assertThat(listedClassification.pages).isEqualTo(EntityValues.DEFAULT_PAGES)
            assertThat(listedClassification.classificationType).isEqualTo(EntityValues.DEFAULT_CLASSIFICATION_TYPE)

            // Update model location
            classificationService.updateModelLocation(classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            val afterModelUpdate = classificationService.loadClassification(classificationId)
            assertThat(afterModelUpdate.info.modelLocation)
                .usingRecursiveComparison()
                .isEqualTo(EntityValues.DEFAULT_STORAGE_LOCATION)

            // Update classification type and pages
            val updatedPages = EntityValues.newClassifiedPages(pages = setOf(3, 4), classification = "CREDIT_CARD")
            classificationService.updateClassification(classificationId, updatedPages)

            val afterClassUpdate = classificationService.loadClassification(classificationId)
            assertThat(afterClassUpdate.pages).isEqualTo(setOf(3, 4))
            assertThat(afterClassUpdate.classificationType).isEqualTo("CREDIT_CARD")

            // List by IDs — verify updated fields appear
            val listedByIds = classificationService.loadClassifications(setOf(classificationId))
            assertThat(listedByIds).hasSize(1)
            assertThat(listedByIds.single().pages).isEqualTo(setOf(3, 4))
            assertThat(listedByIds.single().classificationType).isEqualTo("CREDIT_CARD")

            // Delete
            classificationService.deleteClassifications(listOf(classificationId))

            // Load after delete throws
            assertThatThrownBy { classificationService.loadClassification(classificationId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // List by fileId after delete is empty
            assertThat(classificationService.loadClassifications(fileId)).isEmpty()
        }
    }

    @Nested
    inner class InsertClassifications {

        @Test
        fun `empty classifications list returns empty list without touching the DB`() {
            val result = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = emptyList())
            )
            assertThat(result).isEmpty()
        }

        @Test
        fun `multiple classifications all inserted and returned with correct data`() {
            val pages1 = EntityValues.newClassifiedPages(pages = setOf(1))
            val pages2 = EntityValues.newClassifiedPages(pages = setOf(2))

            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(pages1, pages2))
            )

            assertThat(infos).hasSize(2)
            assertThat(infos.map { it.pages }).containsExactlyInAnyOrder(setOf(1), setOf(2))
            assertThat(infos.map { it.classificationType }).containsOnly(EntityValues.DEFAULT_CLASSIFICATION_TYPE)
            assertThat(infos.map { it.classificationId }.toSet()).hasSize(2) // all IDs are unique
        }
    }

    @Nested
    inner class LoadClassificationsById {

        @Test
        fun `empty set returns empty list`() {
            assertThat(classificationService.loadClassifications(emptySet<UUID>())).isEmpty()
        }

        @Test
        fun `unknown IDs return empty list`() {
            assertThat(classificationService.loadClassifications(setOf(UUID.randomUUID()))).isEmpty()
        }

        @Test
        fun `returns only the classifications matching the given IDs`() {
            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(
                    fileId = fileId,
                    classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(1)),
                        EntityValues.newClassifiedPages(pages = setOf(2)),
                    ),
                )
            )
            val firstId = infos.first().classificationId
            val secondId = infos.last().classificationId

            val result = classificationService.loadClassifications(setOf(firstId))

            assertThat(result).hasSize(1)
            assertThat(result.single().classificationId).isEqualTo(firstId)
            assertThat(result.map { it.classificationId }).doesNotContain(secondId)
        }
    }

    @Nested
    inner class LoadClassificationsByFileId {

        @Test
        fun `returns all classifications for the file with correct fields`() {
            classificationService.insertClassifications(
                EntityValues.newClassifiedFile(
                    fileId = fileId,
                    classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(1)),
                        EntityValues.newClassifiedPages(pages = setOf(2)),
                    ),
                )
            )

            val result = classificationService.loadClassifications(fileId)

            assertThat(result).hasSize(2)
            result.forEach {
                assertThat(it.fileId).isEqualTo(fileId)
                assertThat(it.clientId).isEqualTo(clientId)
            }
            assertThat(result.map { it.pages }).containsExactlyInAnyOrder(setOf(1), setOf(2))
        }

        @Test
        fun `does not return classifications belonging to a different file`() {
            val otherFileId = fileService.insertFile(
                EntityValues.newInputFile(
                    client = EntityValues.newClient(clientId = clientId),
                    info = EntityValues.newInputFileInfo(fileName = "other.pdf"),
                ),
                UUID.randomUUID(),
                UUID.randomUUID(),
            )
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = otherFileId))

            val result = classificationService.loadClassifications(fileId)

            assertThat(result).hasSize(1)
            assertThat(result.single().fileId).isEqualTo(fileId)
        }

        @Test
        fun `no classifications for file returns empty list`() {
            assertThat(classificationService.loadClassifications(fileId)).isEmpty()
        }
    }

    @Nested
    inner class DeleteClassifications {

        @Test
        fun `empty list deletes nothing`() {
            val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            val classificationId = infos.single().classificationId

            classificationService.deleteClassifications(emptyList())

            val stillPresent = classificationService.loadClassification(classificationId)
            assertThat(stillPresent.classificationId).isEqualTo(classificationId)
        }

        @Test
        fun `removes only the specified classifications and leaves others intact`() {
            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(
                    fileId = fileId,
                    classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(1)),
                        EntityValues.newClassifiedPages(pages = setOf(2)),
                    ),
                )
            )
            val idToDelete = infos.first().classificationId
            val idToKeep = infos.last().classificationId

            classificationService.deleteClassifications(listOf(idToDelete))

            assertThatThrownBy { classificationService.loadClassification(idToDelete) }
                .isInstanceOf(EntityNotFoundException::class.java)

            val kept = classificationService.loadClassification(idToKeep)
            assertThat(kept.classificationId).isEqualTo(idToKeep)
        }
    }
}
