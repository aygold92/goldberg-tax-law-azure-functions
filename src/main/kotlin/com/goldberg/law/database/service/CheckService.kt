package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.CheckEntity
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.Classification
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import jdk.internal.module.Checks
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.transactions.transaction
import java.util.UUID

class CheckService @Inject constructor() {
    private val logger = KotlinLogging.logger {}
    /**
     * Save check and return check_id
     */
    fun insertCheck(classification: Classification, check: CheckDetails): UUID = DbExec.txnSafe {
        val newCheck = CheckEntity.new {
            this.classificationId = EntityID(classification.classificationId, ClassificationsTable)
            this.checkNumber = check.checkNumber
            this.accountNumber = check.accountNumber
            this.to = check.to
            this.description = check.description
            this.date = check.date
            this.amount = check.amount
            this.batesStamp = check.batesStamp
        }

        logger.info { "Saved check: ${newCheck.id} for classification: $classification.classificationId" }
        newCheck.id.value
    }

    fun deleteCheck(id: UUID) = DbExec.txnSafe {
        ChecksTable.deleteWhere { ChecksTable.id eq id }
    }
}