package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.*
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.*
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.ZERO
import com.goldberg.law.verify.BankStatementVerifier
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import org.jetbrains.exposed.sql.transactions.transaction
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class StatementService @Inject constructor(
    private val bankStatementVerifier: BankStatementVerifier
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Save bank statement with transactions in one transaction, return statement_id
     */
    fun insertBankStatementWithTransactions(statement: Statement): UUID = transaction {
        // Convert bates stamps to JSON
        val details = statement.statementDetails

        // Insert statement - UUIDTable auto-generates the ID
        val newStatement = BankStatementEntity.new {
            this.classificationId = EntityID(statement.classification.classificationId, ClassificationsTable)
            this.accountNumber = details.accountNumber
            this.date = details.date
            this.beginningBalance = details.beginningBalance
            this.endingBalance = details.endingBalance
            this.interestCharged = details.interestCharged
            this.feesCharged = details.feesCharged
            this.batesStamps = OBJECT_MAPPER.writeValueAsString(details.batesStamps)
        }

        // Batch insert all transactions in batches of 500
        statement.transactions.chunked(DbExec.DEFAULT_BATCH_SIZE).forEach { batch ->
            TransactionsTable.batchInsert(batch) { transaction ->
                this[TransactionsTable.statementId] = newStatement.id
                this[TransactionsTable.checkId] = null as EntityID<UUID>? // Will be linked later if applicable
                this[TransactionsTable.date] = transaction.date
                this[TransactionsTable.checkNumber] = transaction.checkNumber
                this[TransactionsTable.description] = transaction.description
                this[TransactionsTable.amount] = transaction.amount
                this[TransactionsTable.filePageNumber] = transaction.filePageNumber
            }
        }

        logger.info { "Saved bank statement with ${statement.transactions.size} transactions: ${newStatement.id}" }
        newStatement.id.value
    }

    fun updateStatement(statementDetails: StatementDetails) = DbExec.txnSafe {
        BankStatementsTable.update({ BankStatementsTable.id eq statementDetails.statementId }) {
            it[BankStatementsTable.date] = statementDetails.date
            it[BankStatementsTable.accountNumber] = statementDetails.accountNumber
            it[BankStatementsTable.beginningBalance] = statementDetails.beginningBalance
            it[BankStatementsTable.endingBalance] = statementDetails.endingBalance
            it[BankStatementsTable.interestCharged] = statementDetails.interestCharged
            it[BankStatementsTable.feesCharged] = statementDetails.feesCharged
            it[BankStatementsTable.batesStamps] = OBJECT_MAPPER.writeValueAsString(statementDetails.batesStamps)
            it[updatedAt] = Instant.now()
        }
    }

    fun deleteBankStatementWithData(statementId: UUID) = DbExec.txnSafe {
        // Delete transactions first (foreign key constraint)
        TransactionsTable.deleteWhere { TransactionsTable.statementId eq statementId }

        // Delete statement
        BankStatementsTable.deleteWhere { BankStatementsTable.id eq statementId }

        logger.info { "Deleted bank statement and transactions: $statementId" }
    }

    /**
     * Load bank statement from MySQL
     */
    fun loadStatement(statementId: UUID): Statement = DbExec.txnSafe {
        try {
            // Single JOIN query to get all related data
            val (classification, statementDetails) = BankStatementsTable
                .innerJoin(ClassificationsTable, { classificationId }, { ClassificationsTable.id })
                .innerJoin(FilesTable, { ClassificationsTable.fileId }, { FilesTable.id })
                .select(BankStatementsTable.id eq statementId)
                .map { Pair(Classification.fromRow(it), StatementDetails.fromRow(it)) }
                .singleOrNull() ?: throw EntityNotFoundException("Statement $statementId not found").also {
                logger.debug { "Statement not found in MySQL: $statementId" }
            }

            // Load transactions
            val transactionRows = TransactionsTable.select(TransactionsTable.statementId eq statementId)
                .map { TransactionDetails.fromRow(it) }

            val suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(statementDetails, transactionRows, classification)

            Statement(classification, statementDetails, suspiciousReasons, transactionRows)
        } catch (ex: Exception) {
            logger.error(ex) { "Error loading statement from MySQL: $statementId" }
            throw ex
        }
    }

    /**
     * List statements with metadata aggregations
     */
    fun listStatements(clientId: UUID): List<StatementSummary> = DbExec.txnSafe {
        val zeroLiteral = decimalLiteral(BigDecimal.ZERO) as Expression<BigDecimal?>
        val totalSpending = Case()
            .When(TransactionsTable.amount less BigDecimal.ZERO, TransactionsTable.amount)
            .Else(zeroLiteral)
            .sum()
            .alias("totalSpending")

        val totalIncome = Case()
            .When(TransactionsTable.amount greater BigDecimal.ZERO, TransactionsTable.amount)
            .Else(zeroLiteral)
            .sum()
            .alias("totalSpending")

        val numTransactions = TransactionsTable.id.count().alias("numTransactions")

        BankStatementsTable
            .innerJoin(ClassificationsTable, { ClassificationsTable.id }, { BankStatementsTable.classificationId })
            .innerJoin(FilesTable, { FilesTable.id }, { ClassificationsTable.fileId })
            .innerJoin(ClientsTable, { ClientsTable.id }, { FilesTable.clientId })
            .leftJoin(TransactionsTable, { BankStatementsTable.id }, { TransactionsTable.statementId })
            .select(ClientsTable.columns + FilesTable.columns + ClassificationsTable.columns + BankStatementsTable.columns +
                    // computed fields
                    listOf(totalSpending, totalIncome, numTransactions)
            )
            .where { ClientsTable.id eq clientId }
            .groupBy(BankStatementsTable.id)
            .map { row ->
                StatementSummary(
                    classification = Classification.fromRow(row),
                    statementDetails = StatementDetails.fromRow(row),
                    suspiciousReasons = emptyList(),
                    missingChecks = emptySet(),
                    manuallyVerified = false,
                    totalSpending = row[totalSpending] ?: ZERO,
                    totalIncomeCredits = row[totalIncome] ?: ZERO,
                    numTransactions = row[numTransactions].toInt()
                )
            }
    }
}