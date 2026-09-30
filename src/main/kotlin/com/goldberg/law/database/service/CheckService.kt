package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.database.tables.TransactionsTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Check
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityType
import com.goldberg.law.entity.ReviewStatus
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.time.Instant
import java.util.*

class CheckService @Inject constructor(
    private val db: Database
) {
    private val logger = KotlinLogging.logger {}
    /**
     * Save check and return check_id
     */
    fun insertCheck(classification: Classification, check: CheckDetails): UUID = db.txnSafe {
        val newCheckId = ChecksTable.insert {
            it[ChecksTable.classificationId] = EntityID(classification.classificationId, ClassificationsTable)
            it[ChecksTable.checkNumber] = check.checkNumber
            it[ChecksTable.accountNumber] = check.accountNumber
            it[ChecksTable.to] = check.to
            it[ChecksTable.description] = check.description
            it[ChecksTable.date] = check.date
            it[ChecksTable.amount] = check.amount
            it[ChecksTable.batesStamp] = check.batesStamp
            it[ChecksTable.filePageNumber] = check.filePageNumber
            it[ChecksTable.reviewStatus] = check.reviewStatus
        }[ChecksTable.id].value

        logger.info { "Saved check: $newCheckId for classification: ${classification.classificationId}" }
        newCheckId
    }

    fun listChecks(clientId: UUID): List<Check> = db.txnSafe {
        ChecksTable.fullJoin()
            .selectAll().where { ClientsTable.id eq clientId }
            .orderBy(
                ChecksTable.accountNumber to SortOrder.ASC,
                ChecksTable.checkNumber to SortOrder.ASC,
                FilesTable.id to SortOrder.ASC,
            )
            .map { Check.fromRow(it) }
    }

    fun loadCheck(id: UUID) = db.txnSafe {
        ChecksTable.fullJoin()
            .selectAll().where { ChecksTable.id eq id }
            .map { row -> Check.fromRow(row) }
            .singleOrNull() ?: throw EntityNotFoundException(EntityType.Check, id)
    }

    fun deleteCheck(id: UUID) = db.txnSafe {
        ChecksTable.deleteWhere { ChecksTable.id eq id }
            .takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Check, id)
    }

    fun loadCheckIdsForClassification(classificationId: UUID): Set<UUID> = db.txnSafe {
        ChecksTable.selectAll().where { ChecksTable.classificationId eq classificationId }
            .map { row -> row[ChecksTable.id].value }.toSet()
    }

    /** Deletes all checks for a classification. Returns count deleted. */
    fun deleteChecksByClassificationId(classificationId: UUID): Int = db.txnSafe {
        ChecksTable.deleteWhere { ChecksTable.classificationId eq classificationId }
            .also { logger.info { "Deleted $it check(s) for classification $classificationId" } }
    }

    /**
     * Writes every edited field. The review status is left alone — it changes only through [updateReviewStatus] —
     * so a check added here starts with none.
     */
    fun updateChecks(checks: List<CheckDetails>, classificationIds: Map<UUID, UUID>) = db.txnSafe {
        val now = Instant.now()
        ChecksTable.batchUpsert(checks, onUpdateExclude = listOf(ChecksTable.createdAt)) { check ->
            this[ChecksTable.id] = check.checkId
            this[ChecksTable.classificationId] = EntityID(classificationIds.getValue(check.checkId), ClassificationsTable)
            this[ChecksTable.checkNumber] = check.checkNumber
            this[ChecksTable.accountNumber] = check.accountNumber
            this[ChecksTable.to] = check.to
            this[ChecksTable.description] = check.description
            this[ChecksTable.date] = check.date
            this[ChecksTable.amount] = check.amount
            this[ChecksTable.batesStamp] = check.batesStamp
            this[ChecksTable.filePageNumber] = check.filePageNumber
            this[ChecksTable.createdAt] = now
            this[ChecksTable.updatedAt] = now
        }
    }

    fun updateReviewStatus(checkId: UUID, status: ReviewStatus) = db.txnSafe {
        ChecksTable.update({ ChecksTable.id eq checkId }) {
            it[ChecksTable.reviewStatus] = status
            it[updatedAt] = Instant.now()
        }.takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Check, checkId)
        logger.info { "Set review status of check $checkId to $status" }
    }

    /** Deletes existing checks then inserts new ones atomically. Returns the new check IDs. */
    fun replaceChecks(classification: Classification, newChecks: List<CheckDetails>): List<UUID> = db.txnSafe {
        deleteChecksByClassificationId(classification.classificationId)
        newChecks.map { insertCheck(classification, it) }
    }

    companion object {
        fun ChecksTable.classificationsJoin() = innerJoin(ClassificationsTable, { ChecksTable.classificationId }, { id })
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { id })
            .innerJoin(ClientsTable, { FilesTable.clientId }, { id })

        fun ChecksTable.fullJoin() = classificationsJoin()
            .leftJoin(TransactionsTable, { TransactionsTable.checkId }, { ChecksTable.id })
            .leftJoin(BankStatementsTable, { TransactionsTable.statementId }, { BankStatementsTable.id })
    }
}