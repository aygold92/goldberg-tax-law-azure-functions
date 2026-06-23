package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.ClassificationInfo
import com.goldberg.law.entity.EntityType
import com.goldberg.law.entity.ClassifiedFile
import com.goldberg.law.entity.ClassifiedPages
import com.goldberg.law.util.OBJECT_MAPPER
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import com.goldberg.law.database.tables.ChecksTable
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import java.time.Instant
import java.util.*

class ClassificationService @Inject constructor(
    private val db: Database,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * inserts the classifications for a given file.
     * For updates, the caller is responsible for syncing via creation and deletion
     */
    fun insertClassifications(file: ClassifiedFile): List<ClassificationInfo> = if (file.classifications.isEmpty()) emptyList()
    else db.txnSafe {
        val classificationInfos = mutableListOf<ClassificationInfo>()

        // Process classifications in batches of 500
        val now = Instant.now()
        file.classifications.chunked(DbExec.DEFAULT_BATCH_SIZE).forEach { batch ->
            val batchRowResults = ClassificationsTable.batchInsert(batch) { classifiedPdfPages ->
                this[ClassificationsTable.fileId] = file.fileId
                this[ClassificationsTable.pages] = OBJECT_MAPPER.writeValueAsString(classifiedPdfPages.pagesOrdered)
                this[ClassificationsTable.classificationType] = classifiedPdfPages.classification
                this[ClassificationsTable.modelLocation] = null // Will be updated separately when model is saved
                this[ClassificationsTable.createdAt] = now
                this[ClassificationsTable.updatedAt] = now
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
    fun updateModelLocation(classificationId: UUID, modelLocation: StorageLocation) = db.txnSafe {
        ClassificationsTable.update({ ClassificationsTable.id eq classificationId }) {
            it[ClassificationsTable.modelLocation] = modelLocation.serialize()
            it[updatedAt] = Instant.now()
        }.takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Classification, classificationId)
        logger.debug { "Updated model location for classification: $classificationId" }
    }

    fun updateClassification(classificationId: UUID, pages: ClassifiedPages) = db.txnSafe {
        ClassificationsTable.update({ ClassificationsTable.id eq classificationId }) {
            it[ClassificationsTable.pages] = OBJECT_MAPPER.writeValueAsString(pages.pagesOrdered)
            it[ClassificationsTable.classificationType] = pages.classification
            it[updatedAt] = Instant.now()
        }.takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Classification, classificationId)
        logger.debug { "Updated pages for classification: $classificationId" }
    }

    fun loadClassification(classificationId: UUID): Classification = db.txnSafe {
        loadClassifications(setOf(classificationId))
            .singleOrNull()
            ?: throw EntityNotFoundException(EntityType.Classification, classificationId)
    }

    fun loadClassifications(classificationIds: Set<UUID>) = if (classificationIds.isEmpty()) emptyList()
    else db.txnSafe {
        ClassificationsTable.filesJoin()
            .selectAll().where { ClassificationsTable.id inList classificationIds }
            .map { row -> Classification.fromRow(row) }
            .sortedBy { it.pagesOrdered.firstOrNull() }
    }

    fun loadClassifications(fileId: UUID): List<Classification> = db.txnSafe {
        ClassificationsTable.filesJoin()
            .selectAll().where { ClassificationsTable.fileId eq fileId }
            .map { Classification.fromRow(it) }
            .sortedBy { it.pagesOrdered.firstOrNull() }
    }

    fun loadClassificationIdsForChecks(checkIds: List<UUID>): Map<UUID, UUID> =
        if (checkIds.isEmpty()) emptyMap()
        else db.txnSafe {
            ChecksTable
                .selectAll().where { ChecksTable.id inList checkIds }
                .associate { it[ChecksTable.id].value to it[ChecksTable.classificationId].value }
        }

    /**
     * Delete classifications and all related data
     */
    fun deleteClassifications(classificationIds: List<UUID>) = db.txnSafe {
        ClassificationsTable.deleteWhere { ClassificationsTable.id inList classificationIds }
            .also { logger.info { "Deleted $it classifications (requested ${classificationIds.size})" } }
    }



    companion object {
        fun ClassificationsTable.filesJoin() = this
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { id })
            .innerJoin(ClientsTable, { FilesTable.clientId }, { id })
    }
}