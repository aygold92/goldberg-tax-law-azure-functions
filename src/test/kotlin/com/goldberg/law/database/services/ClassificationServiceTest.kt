package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.tables.ChecksTable
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
            Thread.sleep(20) // to ensure creation time is after
            val classifiedFile = EntityValues.newClassifiedFile(fileId = fileId)
            val classificationId = classificationService.insertClassifications(classifiedFile).single().classificationId

            // Load by ID — verify all key fields
            val createdModel = classificationService.loadClassification(classificationId)
            val creationTime = createdModel.info.createdAt
            assertTimeIsDuringTest(creationTime)

            // Update model location
            Thread.sleep(20) // to ensure update time is after
            classificationService.updateModelLocation(classificationId, EntityValues.DEFAULT_STORAGE_LOCATION)
            val afterFirstModelUpdate = classificationService.loadClassification(classificationId)
            val firstUpdateTime = afterFirstModelUpdate.info.updatedAt
            assertTimeInWindow(firstUpdateTime, creationTime)

            // Update classification type and pages
            Thread.sleep(20) // to ensure update time is after
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

        @Test
        fun `bank name, bank source and bates stamps are stored per classification and read back`() {
            val bank = EntityValues.newClassifiedPages(
                setOf(1, 2), "bank_of_america",
                bankName = "Bank of America",
                batesStamps = mapOf(1 to "AG-001", 2 to "AG-002"),
                bankSource = "memory",
            )
            val creditCard = EntityValues.newClassifiedPages(setOf(3, 4), "chase_cc", bankName = "Chase", bankSource = "discovered")

            val infos = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(bank, creditCard))
            )

            assertThat(infos.map { it.bankName }).containsExactly("Bank of America", "Chase")
            val loaded = classificationService.loadClassifications(fileId)
            assertThat(loaded.map { it.bankName }).containsExactly("Bank of America", "Chase")
            assertThat(loaded.first().info.batesStamps).containsExactlyEntriesOf(mapOf(1 to "AG-001", 2 to "AG-002"))
            assertThat(loaded.last().info.batesStamps).isEmpty()
            assertThat(infos.map { it.bankSource }).containsExactly("memory", "discovered")
            assertThat(loaded.map { it.bankSource }).containsExactly("memory", "discovered")
            assertThat(loaded.map { it.info.toClassifiedPages() }).containsExactly(bank, creditCard)
        }

        @Test
        fun `classifications from the Azure pipeline have no bank name, stamps, bank source or unreadable pages`() {
            classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))

            val info = classificationService.loadClassifications(fileId).single().info
            assertThat(info.bankName).isNull()
            assertThat(info.batesStamps).isEmpty()
            assertThat(info.bankSource).isNull()
            assertThat(info.unreadablePages).isEmpty()
        }
    }

    @Nested
    inner class UpdateBankNameAndStamps {

        private fun insertAgentClassification() = classificationService.insertClassifications(
            EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                EntityValues.newClassifiedPages(
                    setOf(1, 2), "chase_cc",
                    bankName = "Chase",
                    batesStamps = mapOf(1 to "AG-001", 2 to "AG-002"),
                )
            ))
        ).single().classificationId

        @Test
        fun `an update carrying the bank name and stamps keeps them`() {
            val classificationId = insertAgentClassification()

            classificationService.updateClassification(classificationId, EntityValues.newClassifiedPages(
                setOf(1, 2, 3), "chase_cc",
                bankName = "Chase",
                batesStamps = mapOf(1 to "AG-001", 2 to "AG-002", 3 to "AG-003"),
            ))

            val info = classificationService.loadClassification(classificationId).info
            assertThat(info.pages).isEqualTo(setOf(1, 2, 3))
            assertThat(info.bankName).isEqualTo("Chase")
            assertThat(info.batesStamps).containsExactlyEntriesOf(mapOf(1 to "AG-001", 2 to "AG-002", 3 to "AG-003"))
        }

        @Test
        fun `an update that edits the bank name and stamps stores the new values`() {
            val classificationId = insertAgentClassification()

            classificationService.updateClassification(classificationId, EntityValues.newClassifiedPages(
                setOf(1, 2), "chase_cc",
                bankName = "Chase Business",
                batesStamps = mapOf(1 to "AG-999", 2 to "AG-002"),
            ))

            val info = classificationService.loadClassification(classificationId).info
            assertThat(info.bankName).isEqualTo("Chase Business")
            assertThat(info.batesStamps).containsEntry(1, "AG-999")
        }

        @Test
        fun `an update leaves what the agents recorded alone`() {
            val classificationId = classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(setOf(1, 2), "chase_cc", bankSource = "memory")
                ))
            ).single().classificationId
            classificationService.updateUnreadablePages(classificationId, listOf(2))

            classificationService.updateClassification(classificationId, EntityValues.newClassifiedPages(setOf(1, 2, 3), "chase_cc"))

            val info = classificationService.loadClassification(classificationId).info
            assertThat(info.pages).isEqualTo(setOf(1, 2, 3))
            assertThat(info.bankSource).isEqualTo("memory")
            assertThat(info.unreadablePages).containsExactly(2)
        }

        @Test
        fun `an update that omits them clears them`() {
            val classificationId = insertAgentClassification()

            classificationService.updateClassification(classificationId, EntityValues.newClassifiedPages(setOf(1, 2), "chase_cc"))

            val info = classificationService.loadClassification(classificationId).info
            assertThat(info.bankName).isNull()
            assertThat(info.batesStamps).isEmpty()
        }
    }

    @Nested
    inner class ExtractionSessions {

        @Test
        fun `extraction session is recorded and the classification is found by it`() {
            val classificationId = classificationService
                .insertClassifications(EntityValues.newClassifiedFile(fileId = fileId)).single().classificationId

            classificationService.updateExtractionSession(classificationId, EntityValues.DEFAULT_EXTRACTION_SESSION_ID)

            val loaded = classificationService.loadClassificationByExtractionSession(EntityValues.DEFAULT_EXTRACTION_SESSION_ID)
            assertThat(loaded.classificationId).isEqualTo(classificationId)
            assertThat(loaded.info.extractionSessionId).isEqualTo(EntityValues.DEFAULT_EXTRACTION_SESSION_ID)
        }

        @Test
        fun `classification with no extraction session has a null session id`() {
            val info = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId)).single()

            assertThat(info.extractionSessionId).isNull()
        }

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy {
                classificationService.updateExtractionSession(UUID.randomUUID(), EntityValues.DEFAULT_EXTRACTION_SESSION_ID)
            }.isInstanceOf(EntityNotFoundException::class.java)
        }

        @Test
        fun `unknown session throws EntityNotFoundException`() {
            assertThatThrownBy {
                classificationService.loadClassificationByExtractionSession("sess_nope")
            }.isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class UnreadablePages {

        private fun insertClassification() = classificationService
            .insertClassifications(EntityValues.newClassifiedFile(fileId = fileId)).single().classificationId

        @Test
        fun `are recorded in page order, loaded with the classification, and replaced by the next run`() {
            val classificationId = insertClassification()

            classificationService.updateUnreadablePages(classificationId, listOf(7, 5))

            val expected = newClassification(inputFile = inputFile, newClassificationInfo(unreadablePages = listOf(5, 7)))
            assertThat(classificationService.loadClassification(classificationId)).entityCompare().isEqualTo(expected)
            assertThat(classificationService.loadClassifications(fileId)).entityCompare().isEqualTo(listOf(expected))

            classificationService.updateUnreadablePages(classificationId, listOf(9))
            assertThat(classificationService.loadClassification(classificationId).info.unreadablePages).containsExactly(9)

            classificationService.updateUnreadablePages(classificationId, emptyList())
            assertThat(classificationService.loadClassification(classificationId).info.unreadablePages).isEmpty()
        }

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy { classificationService.updateUnreadablePages(UUID.randomUUID(), listOf(1)) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class ReplaceClassifications {

        @Test
        fun `replaces the file's existing classifications with the new set`() {
            classificationService.insertClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(setOf(1, 2), DocumentType.BankTypes.B_OF_A),
                ))
            )

            val replaced = classificationService.replaceClassifications(
                EntityValues.newClassifiedFile(fileId = fileId, classifications = listOf(
                    EntityValues.newClassifiedPages(setOf(1, 2, 3), "chase_cc", bankName = "Chase"),
                    EntityValues.newClassifiedPages(setOf(4), DocumentType.CheckTypes.CHECKS),
                )),
            )

            assertThat(replaced).hasSize(2)
            val loaded = classificationService.loadClassifications(fileId)
            assertThat(loaded.map { it.classificationType }).containsExactly("chase_cc", DocumentType.CheckTypes.CHECKS)
            assertThat(loaded.map { it.pages }).containsExactly(setOf(1, 2, 3), setOf(4))
            assertThat(loaded.map { it.bankName }).containsExactly("Chase", null)
        }

        @Test
        fun `replacing a file with no classifications just inserts`() {
            val infos = classificationService.replaceClassifications(EntityValues.newClassifiedFile(fileId = fileId))

            assertThat(infos).hasSize(1)
            assertThat(classificationService.loadClassifications(fileId)).hasSize(1)
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
    inner class LoadClassificationIdsForChecks {
        private val checkService = CheckService(db)

        @BeforeEach
        fun clearChecks() {
            db.txnSafe { ChecksTable.deleteAll() }
        }

        @Test
        fun `empty list returns empty map`() {
            assertThat(classificationService.loadClassificationIdsForChecks(emptyList())).isEmpty()
        }

        @Test
        fun `unknown checkId is not included in result`() {
            val result = classificationService.loadClassificationIdsForChecks(listOf(UUID.randomUUID()))
            assertThat(result).isEmpty()
        }

        @Test
        fun `returns correct classificationId for a known check`() {
            val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            val classification = classificationService.loadClassification(infos.single().classificationId)
            val checkId = checkService.insertCheck(classification, EntityValues.newCheckDetails())

            val result = classificationService.loadClassificationIdsForChecks(listOf(checkId))

            assertThat(result).hasSize(1)
            assertThat(result[checkId]).isEqualTo(classification.classificationId)
        }

        @Test
        fun `returns entries for all matching checks and omits unknown ids`() {
            val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
            val classification = classificationService.loadClassification(infos.single().classificationId)
            val checkId1 = checkService.insertCheck(classification, EntityValues.newCheckDetails(checkNumber = 1))
            val checkId2 = checkService.insertCheck(classification, EntityValues.newCheckDetails(checkNumber = 2))
            val unknownId = UUID.randomUUID()

            val result = classificationService.loadClassificationIdsForChecks(listOf(checkId1, checkId2, unknownId))

            assertThat(result).hasSize(2)
            assertThat(result[checkId1]).isEqualTo(classification.classificationId)
            assertThat(result[checkId2]).isEqualTo(classification.classificationId)
            assertThat(result).doesNotContainKey(unknownId)
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
