package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.*
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.EntityType
import com.goldberg.law.entity.Transaction
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.transactions.transaction
import java.time.Instant
import java.util.*

class TransactionService @Inject constructor(
    private val db: Database,
) {
    private val logger = KotlinLogging.logger {}

    fun batchInsert(statementId: UUID, transactions: List<TransactionDetails>) {
        transactions.mapIndexed { index, t -> index to t }
            .chunked(DbExec.DEFAULT_BATCH_SIZE)
            .forEach { batch ->
                TransactionsTable.batchInsert(batch) { (index, transaction) ->
                    this[TransactionsTable.statementId] = statementId
                    this[TransactionsTable.checkId] = null as EntityID<UUID>? // Will be linked later if applicable
                    this[TransactionsTable.date] = transaction.date
                    this[TransactionsTable.checkNumber] = transaction.checkNumber
                    this[TransactionsTable.description] = transaction.description
                    this[TransactionsTable.amount] = transaction.amount
                    this[TransactionsTable.filePageNumber] = transaction.filePageNumber
                    this[TransactionsTable.statementIndex] = index
                }
            }
        logger.info { "Inserting ${transactions.size} for: $statementId" }
    }

    fun upsertTransactions(statementId: UUID, transactions: List<TransactionDetails>) = db.txnSafe {
        val now = Instant.now()
        TransactionsTable.batchUpsert(transactions, onUpdateExclude = listOf(TransactionsTable.createdAt)) { transaction ->
            this[TransactionsTable.statementId] = statementId
            this[TransactionsTable.id] = transaction.transactionId
            this[TransactionsTable.checkId] = transaction.checkId
            this[TransactionsTable.date] = transaction.date
            this[TransactionsTable.checkNumber] = transaction.checkNumber
            this[TransactionsTable.description] = transaction.description
            this[TransactionsTable.amount] = transaction.amount
            this[TransactionsTable.filePageNumber] = transaction.filePageNumber
            this[TransactionsTable.statementIndex] = transaction.statementIndex
            this[TransactionsTable.createdAt] = now
            this[TransactionsTable.updatedAt] = now
        }
    }

    fun loadTransactionsByStatement(statementIds: List<UUID>): Map<UUID, List<TransactionDetails>> = db.txnSafe {
        TransactionsTable
            .selectAll().where { TransactionsTable.statementId inList statementIds }
            .orderBy(TransactionsTable.statementIndex to SortOrder.ASC, TransactionsTable.filePageNumber to SortOrder.ASC, TransactionsTable.date to SortOrder.ASC)
            .map { row -> row[TransactionsTable.statementId].value to TransactionDetails.fromRow(row) }
            .groupBy({ it.first }, { it.second })
    }

    fun loadTransactions(statementId: UUID): List<TransactionDetails> = db.txnSafe {
        TransactionsTable
            .selectAll().where { TransactionsTable.statementId eq statementId }
            .orderBy(TransactionsTable.statementIndex to SortOrder.ASC, TransactionsTable.filePageNumber to SortOrder.ASC, TransactionsTable.date to SortOrder.ASC)
            .map { TransactionDetails.fromRow(it) }
    }

    fun loadTransaction(transactionId: UUID): TransactionDetails = db.txnSafe {
        TransactionsTable
            .selectAll().where { TransactionsTable.id eq transactionId }
            .map { TransactionDetails.fromRow(it) }
            .singleOrNull() ?: throw EntityNotFoundException(EntityType.Transaction, transactionId)
    }

    fun deleteTransactions(transactionIds: List<UUID>) = db.txnSafe {
        TransactionsTable.deleteWhere { TransactionsTable.id inList transactionIds }
    }

    fun findMatchingChecks(clientId: UUID): List<Transaction> = db.txnSafe {
        // Join transactions with bank statements, then join with ChecksTable
        // Join on checkNumber, then match accountNumber in WHERE clause
        // Also join through ClassificationsTable and FilesTable to filter by clientId
        TransactionsTable
            .innerJoin(BankStatementsTable, { TransactionsTable.statementId }, { BankStatementsTable.id })
            .innerJoin(ClassificationsTable, { BankStatementsTable.classificationId }, { ClassificationsTable.id })
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { FilesTable.id })
            .innerJoin(ChecksTable, { TransactionsTable.checkNumber }, { ChecksTable.checkNumber }, { BankStatementsTable.accountNumber eq ChecksTable.accountNumber })
            .selectAll().where { (FilesTable.clientId eq clientId) and (TransactionsTable.checkId eq null) }
            .map { row -> Transaction.fromRow(row) }
    }

    fun linkTransactionsWithChecks(transactionCheckMatches: List<TransactionCheckMatch>) = db.txnSafe {
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

    fun reorderTransactions(statementId: UUID, orderedTransactionIds: List<UUID>) = db.txnSafe {
        val now = Instant.now()
        orderedTransactionIds.forEachIndexed { index, transactionId ->
            TransactionsTable.update({
                (TransactionsTable.id eq transactionId) and (TransactionsTable.statementId eq statementId)
            }) {
                it[TransactionsTable.statementIndex] = index
                it[updatedAt] = now
            }
        }
    }

}