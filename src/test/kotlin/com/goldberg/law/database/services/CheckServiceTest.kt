package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Check
import com.goldberg.law.entity.ClassifiedCheck
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.deleteAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class CheckServiceTest : DatabaseTest() {

    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val classificationService = ClassificationService(db)
    private val checkService = CheckService(db)

    // Shared upstream fixtures
    private lateinit var clientId: UUID
    private lateinit var fileId: UUID
    private lateinit var classification: Classification

    @BeforeAll
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
        )
        val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
        classification = classificationService.loadClassification(infos.single().classificationId)
    }

    @BeforeEach
    fun clearChecks() {
        db.txnSafe {
            ChecksTable.deleteAll()
        }
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, verify all fields via loadFilesToProcess, then delete`() {
            // CheckService has no loadCheck method; round-trip field verification is done
            // via fileService.loadFilesToProcess which returns ClassifiedCheck items.
            val checkDetails = EntityValues.newCheckDetails()
            val checkId = checkService.insertCheck(classification, checkDetails)

            // Verify stored fields
            val actualCheck = checkService.loadCheck(checkId)

            assertThat(actualCheck).entityCompare().isEqualTo(Check(classification, checkDetails))

            // Delete
            val deletedCount = checkService.deleteCheck(checkId)
            assertThat(deletedCount).isEqualTo(1)

            // Verify gone — loadFilesToProcess now returns the bare classification, not a check
            assertThatThrownBy {
                checkService.loadCheck(checkId)
            }.isInstanceOf(EntityNotFoundException::class.java)
            assertThat(classificationService.loadClassification(classification.classificationId)).isEqualTo(classification) // classification still exists
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
    inner class InsertCheckNullableFields {

        @Test
        fun `check with all nullable fields null round-trips correctly`() {
            val checkDetails = EntityValues.newCheckDetails(
                checkNumber = null,
                accountNumber = null,
                description = null,
                date = null,
                amount = null,
                to = null,
                batesStamp = null,
            )
            val checkId = checkService.insertCheck(classification, checkDetails)
            val loaded = checkService.loadCheck(checkId)

            assertThat(loaded).entityCompare().isEqualTo(Check(classification, checkDetails))
            assertThat(loaded.checkNumber).isNull()
            assertThat(loaded.amount).isNull()
            assertThat(loaded.date).isNull()
        }
    }

    @Nested
    inner class DeleteCheck {

        @Test
        fun `non-existent ID throws EntityNotFoundException`() {
            assertThatThrownBy { checkService.deleteCheck(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }
}
