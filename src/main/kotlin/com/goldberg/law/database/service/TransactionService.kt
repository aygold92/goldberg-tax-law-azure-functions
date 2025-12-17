package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.database.tables.TransactionsTable
import com.goldberg.law.database.tables.TransactionsTable.updatedAt
import com.goldberg.law.entity.Transaction
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.and
import org.jetbrains.exposed.sql.batchUpsert
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.innerJoin
import org.jetbrains.exposed.sql.update
import java.time.Instant
import java.util.UUID
import kotlin.collections.forEach

class TransactionService @Inject constructor() {
    private val logger = KotlinLogging.logger {}

    fun upsertTransactions(statementId: UUID, transactions: List<TransactionDetails>) {
        TransactionsTable.batchUpsert(transactions) { transaction ->
            this[TransactionsTable.statementId] = statementId
            this[TransactionsTable.checkId] = transaction.checkId
            this[TransactionsTable.date] = transaction.date
            this[TransactionsTable.checkNumber] = transaction.checkNumber
            this[TransactionsTable.description] = transaction.description
            this[TransactionsTable.amount] = transaction.amount
            this[TransactionsTable.filePageNumber] = transaction.filePageNumber
            this[TransactionsTable.updatedAt] = Instant.now()
        }
    }

    fun deleteTransactions(transactionIds: List<UUID>) {
        TransactionsTable.deleteWhere { TransactionsTable.id inList transactionIds }
    }

    fun findMatchingChecks(clientId: UUID): List<Transaction> = DbExec.txnSafe {
        // Join transactions with bank statements, then join with ChecksTable
        // Join on checkNumber, then match accountNumber in WHERE clause
        // Also join through ClassificationsTable and FilesTable to filter by clientId
        TransactionsTable
            .innerJoin(BankStatementsTable, { TransactionsTable.statementId }, { BankStatementsTable.id })
            .innerJoin(ClassificationsTable, { BankStatementsTable.classificationId }, { ClassificationsTable.id })
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { FilesTable.id })
            .innerJoin(ChecksTable, { TransactionsTable.checkNumber }, { ChecksTable.checkNumber }, { BankStatementsTable.accountNumber eq ChecksTable.accountNumber })
            .select((FilesTable.clientId eq clientId) and (TransactionsTable.checkId eq null))
            .map { row -> Transaction.fromRow(row) }
    }

    fun linkTransactionsWithChecks(transactionCheckMatches: List<TransactionCheckMatch>) = DbExec.txnSafe {
        // Batch insert all transactions in batches of 500
        val now = Instant.now()
        transactionCheckMatches.forEach { (transactionId, checkId) ->
            TransactionsTable.update({ TransactionsTable.id eq transactionId }) {
                it[TransactionsTable.checkId] = EntityID(checkId, ChecksTable)
                it[updatedAt] = now
            }
        }

        logger.info { "Saved checkId for ${transactionCheckMatches.size} transactions" }
    }
}