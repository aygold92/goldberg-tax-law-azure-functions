package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Check
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityType
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
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
        }[ChecksTable.id].value

        logger.info { "Saved check: $newCheckId for classification: ${classification.classificationId}" }
        newCheckId
    }

    fun loadCheck(id: UUID) = db.txnSafe {
        ChecksTable.classificationsJoin()
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

    /** Deletes existing checks then inserts new ones atomically. Returns the new check IDs. */
    fun replaceChecks(classification: Classification, newChecks: List<CheckDetails>): List<UUID> = db.txnSafe {
        deleteChecksByClassificationId(classification.classificationId)
        newChecks.map { insertCheck(classification, it) }
    }

    companion object {
        fun ChecksTable.classificationsJoin() = innerJoin(ClassificationsTable, { ChecksTable.classificationId }, { id })
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { id })
            .innerJoin(ClientsTable, { FilesTable.clientId }, { id })

    }
}