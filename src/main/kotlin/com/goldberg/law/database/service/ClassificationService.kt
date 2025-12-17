package com.goldberg.law.database.service

import com.goldberg.law.database.DatabaseConfig
import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.*
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassificationInfo
import com.goldberg.law.entity.ClassifiedFile
import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.Instant
import java.util.*

class ClassificationService @Inject constructor(private val statementService: StatementService) {
    private val logger = KotlinLogging.logger {}

    /**
     * inserts the classifications for a given file.
     * For updates, the caller is responsible for syncing via creation and deletion
     */
    fun insertClassifications(file: ClassifiedFile): List<ClassificationInfo> = if (file.classifications.isEmpty()) emptyList()
    else DbExec.txnSafe {
        val classificationInfos = mutableListOf<ClassificationInfo>()

        // Process classifications in batches of 500
        file.classifications.chunked(DbExec.DEFAULT_BATCH_SIZE).forEach { batch ->
            val batchRowResults = ClassificationsTable.batchInsert(batch) { classifiedPdfPages ->
                this[ClassificationsTable.fileId] = file.fileId
                this[ClassificationsTable.pages] = OBJECT_MAPPER.writeValueAsString(classifiedPdfPages.pagesOrdered)
                this[ClassificationsTable.classificationType] = classifiedPdfPages.classification
                this[ClassificationsTable.modelLocation] = null // Will be updated separately when model is saved
            }

            classificationInfos.addAll(batchRowResults.map { ClassificationInfo.fromRow(it) })
        }

        logger.info {
            val numBatches = (file.classifications.size + DbExec.DEFAULT_BATCH_SIZE - 1) / DbExec.DEFAULT_BATCH_SIZE
            "Batch inserted ${file.classifications.size} classifications for file: ${file.fileId} in $numBatches batches"
        }
        classificationInfos
    }

    /**
     * Update model location for a classification
     */
    fun updateModelLocation(classificationId: UUID, modelLocation: StorageLocation) = DbExec.txnSafe {
        ClassificationsTable.update({ ClassificationsTable.id eq classificationId }) {
            it[ClassificationsTable.modelLocation] = modelLocation.serialize()
            it[updatedAt] = Instant.now()
        }
        logger.debug { "Updated model location for classification: $classificationId" }
    }

    fun updateClassification(classificationId: UUID, pages: ClassifiedPages) = DbExec.txnSafe {
        ClassificationsTable.update({ ClassificationsTable.id eq classificationId }) {
            it[ClassificationsTable.pages] = OBJECT_MAPPER.writeValueAsString(pages.pagesOrdered)
            it[ClassificationsTable.classificationType] = pages.classification
            it[updatedAt] = Instant.now()
        }
        logger.debug { "Updated pages for classification: $classificationId" }
    }

    fun loadClassification(classificationId: UUID): Classification = DbExec.txnSafe {
        loadClassifications(setOf(classificationId))
            .singleOrNull()
            ?: throw EntityNotFoundException("Classification not found: $classificationId")
    }

    fun loadClassifications(classificationIds: Set<UUID>) = if (classificationIds.isEmpty()) emptyList()
    else DbExec.txnSafe {
        ClassificationsTable
            .leftJoin(FilesTable, { ClassificationsTable.fileId }, { FilesTable.id })
            .leftJoin(ClientsTable, { FilesTable.clientId }, { ClientsTable.id })
            .select(ClassificationsTable.id inList classificationIds)
            .map { row -> Classification.fromRow(row) }
    }

    fun loadClassifications(fileId: UUID): List<Classification> = DbExec.txnSafe {
        ClassificationsTable
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { FilesTable.id })
            .innerJoin(ClientsTable, { FilesTable.clientId }, { ClientsTable.id })
            .select(ClassificationsTable.fileId eq fileId)
            .map { Classification.fromRow(it) }
    }

    /**
     * Delete classifications and all related data
     */
    fun deleteClassifications(classificationIds: List<UUID>) = DbExec.txnSafe {
        for (classificationId in classificationIds) {
            deleteClassification(classificationId)
        }
        logger.info { "Deleted ${classificationIds.size} classifications" }
    }

    private fun deleteClassification(classificationId: UUID) = DbExec.txnSafe {

        // Delete related statements and transactions
        val statementIds = BankStatementsTable.select(BankStatementsTable.classificationId eq classificationId)
            .map { it[BankStatementsTable.id].value }

        for (statementId in statementIds) {
            statementService.deleteBankStatementWithData(statementId)
        }

        // Delete related checks (must delete transactions first due to FK constraint)
        ChecksTable.deleteWhere { ChecksTable.classificationId eq classificationId }

        // Delete classification
        ClassificationsTable.deleteWhere { ClassificationsTable.id eq classificationId }

        logger.debug { "Deleted classification: $classificationId" }
    }
}