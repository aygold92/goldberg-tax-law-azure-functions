package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassificationInfo
import com.goldberg.law.entity.InputFile
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class ClassificationServiceTest : DatabaseTest() {

    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val classificationService = ClassificationService(db)

    // Shared upstream fixtures
    private lateinit var clientId: UUID
    private lateinit var fileId: UUID
    private lateinit var inputFile: InputFile

    @BeforeAll
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        inputFile = EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId))
        fileId = fileService.insertFile(
            inputFile,
            UUID.randomUUID(),
        )
    }

    @BeforeEach
    fun clearClassifications() {
        db.txnSafe {
            ClassificationsTable.deleteAll()
        }
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
            assertThat(info).entityCompare().isEqualTo(newClassificationInfo())
            assertThat(info.modelLocation).isNull()
            val classificationId = info.classificationId

            // Load by ID — verify all key fields
            val loaded = classificationService.loadClassification(classificationId)
            assertThat(loaded).entityCompare().isEqualTo(newClassification(inputFile = inputFile))
            assertThat(loaded.classificationId).isEqualTo(classificationId)
            assertThat(loaded.fileId).isEqualTo(fileId)
            assertThat(loaded.clientId).isEqualTo(clientId)

            // List by fileId — verify appears with correct fields
            val listedByFile = classificationService.loadClassifications(fileId)
            assertThat(listedByFile).hasSize(1)
            val listedClassification = listedByFile.single()
            assertThat(loaded).entityCompare().isEqualTo(newClassification(inputFile = inputFile))
            assertThat(listedClassification.classificationId).isEqualTo(classificationId)
            assertThat(listedClassification.fileId).isEqualTo(fileId)
            assertThat(listedClassification.clientId).isEqualTo(clientId)

            // Update model location
            classificationService.updateModelLocation(classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            val afterModelUpdate = classificationService.loadClassification(classificationId)
            assertThat(afterModelUpdate).entityCompare()
                .isEqualTo(newClassification(inputFile = inputFile, newClassificationInfo(modelLocation = EntityValues.DEFAULT_STORAGE_LOCATION)))

            // Update classification type and pages
            val cfnType = "CREDIT_CARD"
            val updatedPages = EntityValues.newClassifiedPages(pages = setOf(3, 4), classification = cfnType)
            classificationService.updateClassification(classificationId, updatedPages)

            val actualAfterClassUpdate = classificationService.loadClassification(classificationId)
            val expectedAfterClassUpdate = newClassification(inputFile = inputFile, newClassificationInfo(
                modelLocation = EntityValues.DEFAULT_STORAGE_LOCATION,
                pages = setOf(3, 4),
                classificationType = cfnType
            ))
            assertThat(actualAfterClassUpdate).entityCompare().isEqualTo(expectedAfterClassUpdate)

            // List by IDs — verify updated fields appear
            val listedByIds = classificationService.loadClassifications(setOf(classificationId))
            assertThat(listedByIds).hasSize(1)
            assertThat(listedByIds.single()).entityCompare().isEqualTo(expectedAfterClassUpdate)

            // Delete
            classificationService.deleteClassifications(listOf(classificationId))

            // Load after delete throws
            assertThatThrownBy { classificationService.loadClassification(classificationId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // List by fileId after delete is empty
            assertThat(classificationService.loadClassifications(fileId)).isEmpty()
        }

        @Test
        fun `test timing`() {
            val classifiedFile = EntityValues.newClassifiedFile(fileId = fileId)
            val classificationId = classificationService.insertClassifications(classifiedFile).single().classificationId

            // Load by ID — verify all key fields
            Thread.sleep(5)
            val createdModel = classificationService.loadClassification(classificationId)
            val creationTime = createdModel.info.createdAt
            assertTimeIsDuringTest(creationTime)

            // Update model location
            Thread.sleep(5) // to ensure update time is after
            classificationService.updateModelLocation(classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            val afterFirstModelUpdate = classificationService.loadClassification(classificationId)
            val firstUpdateTime = afterFirstModelUpdate.info.updatedAt
            assertTimeInWindow(firstUpdateTime, creationTime)

            // Update classification type and pages
            Thread.sleep(5) // to ensure update time is after
            val updatedPages = EntityValues.newClassifiedPages(pages = setOf(3, 4))
            classificationService.updateClassification(classificationId, updatedPages)

            val afterSecondModelUpdate = classificationService.loadClassification(classificationId)
            val secondUpdateTime = afterSecondModelUpdate.info.updatedAt

            assertTimeInWindow(secondUpdateTime, firstUpdateTime)

            assertThat(creationTime)
                .isEqualTo(afterFirstModelUpdate.info.createdAt)
                .isEqualTo(afterSecondModelUpdate.info.createdAt)
                .isEqualTo(createdModel.info.updatedAt)
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
        fun `duplicate pages for same file throws exception`() {
            classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(pages = setOf(1, 2))
                ))
            )
            assertThatThrownBy {
                classificationService.insertClassifications(
                    EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                        EntityValues.newClassifiedPages(pages = setOf(1, 2))
                    ))
                )
            }
        }

        @Test
        fun `multiple classifications all inserted and returned with correct data`() {
            val pages1 = EntityValues.newClassifiedPages(setOf(1, 2), DocumentType.BankTypes.B_OF_A)
            val pages2 = EntityValues.newClassifiedPages(setOf(3, 4), DocumentType.BankTypes.WF_BANK)

            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(pages1, pages2))
            )

            assertThat(infos).hasSize(2)
            assertThat(infos).entityCompare().isEqualTo(listOf(
                newClassificationInfo(
                    pages = setOf(1, 2),
                    classificationType = DocumentType.BankTypes.B_OF_A
                ),
                newClassificationInfo(
                    pages =setOf(3, 4),
                    classificationType = DocumentType.BankTypes.WF_BANK
                ),
            ))

            assertThat(infos.map { it.classificationId }.toSet()).hasSize(2) // all IDs are unique
        }
    }

    @Nested
    inner class UpdateModelLocation {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy {
                classificationService.updateModelLocation(UUID.randomUUID(), EntityValues.DEFAULT_STORAGE_LOCATION)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class UpdateClassification {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy {
                classificationService.updateClassification(UUID.randomUUID(), EntityValues.newClassifiedPages())
            }.isInstanceOf(EntityNotFoundException::class.java)
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
                    info = EntityValues.newInputFileInfo(fileName = "other.pdf", contentHash = UUID.randomUUID()),
                ),
                UUID.randomUUID(),
            )
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = otherFileId))

            val result = classificationService.loadClassifications(fileId)

            assertThat(result).hasSize(1)
            assertThat(result.single().fileId).isEqualTo(fileId)

            fileService.deleteInputFile(otherFileId)
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
