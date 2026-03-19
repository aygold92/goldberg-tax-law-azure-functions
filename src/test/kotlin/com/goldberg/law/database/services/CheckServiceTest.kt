package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.entity.ClassifiedCheck
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class CheckServiceTest : DatabaseTest() {

    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val statementService = StatementService(bankStatementVerifier, db)
    private val classificationService = ClassificationService(statementService, db)

    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val checkService = CheckService(db)

    // Shared upstream fixtures — recreated before each test.
    // insertCheck is under test here, so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID
    private lateinit var fileId: UUID
    private lateinit var classification: Classification

    @BeforeEach
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
        val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
        classification = classificationService.loadClassification(infos.single().classificationId)
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, verify all fields via loadFilesToProcess, then delete`() {
            // CheckService has no loadCheck method; round-trip field verification is done
            // via fileService.loadFilesToProcess which returns ClassifiedCheck items.
            val checkDetails = EntityValues.newCheckDetails()
            val checkId = checkService.insertCheck(classification, checkDetails)

            // Verify stored fields via loadFilesToProcess
            val (inputFiles, classifications, classifiedItems) = fileService.loadFilesToProcess(setOf(fileId))
            assertThat(inputFiles).isEmpty()
            assertThat(classifications).isEmpty()
            assertThat(classifiedItems).hasSize(1)
            val classifiedCheck = classifiedItems.single() as ClassifiedCheck
            assertThat(classifiedCheck.checkDetails.checkId).isEqualTo(checkId)
            assertThat(classifiedCheck.checkDetails)
                .usingRecursiveComparison()
                .ignoringFields("checkId")
                .isEqualTo(checkDetails)

            // Delete
            val deletedCount = checkService.deleteCheck(checkId)
            assertThat(deletedCount).isEqualTo(1)

            // Verify gone — loadFilesToProcess now returns the bare classification, not a check
            val (_, classificationsAfter, classifiedItemsAfter) = fileService.loadFilesToProcess(setOf(fileId))
            assertThat(classifiedItemsAfter).isEmpty()
            assertThat(classificationsAfter).hasSize(1) // classification still exists

            // Deleting again returns 0
            assertThat(checkService.deleteCheck(checkId)).isEqualTo(0)
        }

        @Test
        fun `multiple checks for same classification each get a unique ID`() {
            val checkId1 = checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = 1001),
            )
            val checkId2 = checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = 1002),
            )

            assertThat(checkId1).isNotEqualTo(checkId2)

            val (_, _, classifiedItems) = fileService.loadFilesToProcess(setOf(fileId))
            assertThat(classifiedItems).hasSize(2)
            val checkNumbers = classifiedItems.map { (it as ClassifiedCheck).checkDetails.checkNumber }
            assertThat(checkNumbers).containsExactlyInAnyOrder(1001, 1002)
        }
    }

    @Nested
    inner class DeleteCheck {

        @Test
        fun `non-existent ID returns 0`() {
            assertThat(checkService.deleteCheck(UUID.randomUUID())).isEqualTo(0)
        }
    }
}
