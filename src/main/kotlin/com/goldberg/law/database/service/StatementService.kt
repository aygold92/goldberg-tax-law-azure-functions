package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.*
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.*
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.verify.BankStatementVerifier
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.greater
import org.jetbrains.exposed.sql.SqlExpressionBuilder.less
import java.math.BigDecimal
import java.time.Instant
import java.util.*

class StatementService @Inject constructor(
    private val transactionService: TransactionService,
    private val bankStatementVerifier: BankStatementVerifier,
    private val db: Database,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Save bank statement with transactions in one transaction, return statement_id
     */
    fun insertBankStatementWithTransactions(statement: Statement): UUID = db.txnSafe {
        // Convert bates stamps to JSON
        val details = statement.statementDetails

        val newStatementId = BankStatementsTable.insert {
            it[BankStatementsTable.classificationId] = EntityID(statement.classification.classificationId, ClassificationsTable)
            it[BankStatementsTable.accountNumber] = details.accountNumber
            it[BankStatementsTable.date] = details.date
            it[BankStatementsTable.beginningBalance] = details.beginningBalance
            it[BankStatementsTable.endingBalance] = details.endingBalance
            it[BankStatementsTable.interestCharged] = details.interestCharged
            it[BankStatementsTable.feesCharged] = details.feesCharged
            it[BankStatementsTable.batesStamps] = OBJECT_MAPPER.writeValueAsString(details.batesStamps)
        }[BankStatementsTable.id]

        // Batch insert all transactions in batches of 500
        transactionService.batchInsert(newStatementId.value, statement.transactions)

        logger.info { "Saved bank statement with ${statement.transactions.size} transactions: $newStatementId" }
        newStatementId.value
    }

    fun updateBankStatement(statementDetails: StatementDetails) = db.txnSafe {
        BankStatementsTable.update({ BankStatementsTable.id eq statementDetails.statementId }) {
            it[BankStatementsTable.date] = statementDetails.date
            it[BankStatementsTable.accountNumber] = statementDetails.accountNumber
            it[BankStatementsTable.beginningBalance] = statementDetails.beginningBalance
            it[BankStatementsTable.endingBalance] = statementDetails.endingBalance
            it[BankStatementsTable.interestCharged] = statementDetails.interestCharged
            it[BankStatementsTable.feesCharged] = statementDetails.feesCharged
            it[BankStatementsTable.batesStamps] = OBJECT_MAPPER.writeValueAsString(statementDetails.batesStamps)
            it[updatedAt] = Instant.now()
        }.takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Statement, statementDetails.statementId)
    }

    fun deleteBankStatement(statementId: UUID) = db.txnSafe {
        BankStatementsTable.deleteWhere { BankStatementsTable.id eq statementId }
            .takeUnless { it == 0 }?.also { logger.info { "Deleted bank statement with $statementId" } }
            ?: throw EntityNotFoundException(EntityType.Statement, statementId)
                .also { logger.info { "Requested delete bank statement but not found for [$statementId]" }
        }
    }

    fun loadStatementIdsForClassification(classificationId: UUID): Set<UUID> = db.txnSafe {
        BankStatementsTable.selectAll().where { BankStatementsTable.classificationId eq classificationId }
            .map { row -> row[BankStatementsTable.id].value }.toSet()
    }

    /** Deletes all statements (and their transactions via CASCADE) for a classification. Returns count deleted. */
    fun deleteStatementsByClassificationId(classificationId: UUID): Int = db.txnSafe {
        BankStatementsTable.deleteWhere { BankStatementsTable.classificationId eq classificationId }
            .also { logger.info { "Deleted $it statement(s) for classification $classificationId" } }
    }

    /** Deletes existing statements then inserts new ones atomically. Returns the new statement IDs. */
    fun replaceStatements(classificationId: UUID, newStatements: List<Statement>): List<UUID> = db.txnSafe {
        deleteStatementsByClassificationId(classificationId)
        newStatements.map { insertBankStatementWithTransactions(it) }
    }

    /**
     * Load bank statement from MySQL
     */
    fun loadBankStatement(statementId: UUID): Statement = db.txnSafe {
        try {
            // First query the statement with related data
            val (classification, statementDetails) = BankStatementsTable.classificationsJoin()
                .selectAll().where { BankStatementsTable.id eq statementId }
                .map { Pair(Classification.fromRow(it), StatementDetails.fromRow(it)) }
                .singleOrNull() ?: throw EntityNotFoundException(EntityType.Statement, statementId).also {
                logger.debug { "Statement not found in MySQL: $statementId" }
            }

            // Then Load transactions for that statement
            val transactionRows = transactionService.loadTransactions(statementId)

            val suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(statementDetails, transactionRows, classification)

            Statement(classification, statementDetails, suspiciousReasons, transactionRows)
        } catch (ex: Exception) {
            logger.error(ex) { "Error loading statement from MySQL: $statementId" }
            throw ex
        }
    }

    fun loadStatementSummary(statementId: UUID) = db.txnSafe {
        loadStatementSummaries { BankStatementsTable.id eq statementId }.singleOrNull()
            ?: throw EntityNotFoundException(EntityType.Statement, statementId).also {
                logger.debug { "Statement not found in MySQL: $statementId" }
            }
    }

    /**
     * List statements with metadata aggregations, suspicious reasons, and missing checks.
     * Transactions are batch-loaded in a single follow-up query to avoid N+1.
     */
    fun listBankStatements(clientId: UUID): List<StatementSummary> = db.txnSafe {
        loadStatementSummaries { ClientsTable.id eq clientId }
    }

    private fun loadStatementSummaries(where: SqlExpressionBuilder.() -> Op<Boolean>): List<StatementSummary> {
        val zeroLiteral = decimalLiteral(ZERO) as Expression<BigDecimal?>
        val spendingAlias = Case()
            .When(TransactionsTable.amount less BigDecimal.ZERO, TransactionsTable.amount)
            .Else(zeroLiteral)
            .sum()
            .alias("totalSpending")

        val incomeAlias = Case()
            .When(TransactionsTable.amount greater BigDecimal.ZERO, TransactionsTable.amount)
            .Else(zeroLiteral)
            .sum()
            .alias("totalIncome")

        val numTransactions = TransactionsTable.id.countDistinct().alias("numTransactions")

        val statementRows = BankStatementsTable.joinTransactions()
            .select(ClientsTable.columns + FilesTable.columns + ClassificationsTable.columns + BankStatementsTable.columns +
                    listOf(spendingAlias, incomeAlias, numTransactions)
            )
            .where(where)
            .groupBy(BankStatementsTable.id)
            .orderBy(
                BankStatementsTable.accountNumber to SortOrder.ASC,
                BankStatementsTable.date to SortOrder.ASC,
                FilesTable.id to SortOrder.ASC,
                BankStatementsTable.createdAt to SortOrder.DESC
            )
            .toList()

        if (statementRows.isEmpty()) return emptyList()

        val statementIds = statementRows.map { it[BankStatementsTable.id].value }
        val transactionsByStatement = transactionService.loadTransactionsByStatement(statementIds)

        return statementRows.map { row ->
            val statementDetails = StatementDetails.fromRow(row)
            val classification = Classification.fromRow(row)
            val transactions = transactionsByStatement[statementDetails.statementId] ?: emptyList()
            StatementSummary(
                classification = classification,
                statementDetails = statementDetails,
                suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(statementDetails, transactions, classification),
                missingChecks = transactions
                    .filter { it.checkNumber != null && it.checkId == null }
                    .map { it.checkNumber.toString() }
                    .toSet(),
                manuallyVerified = false,
                totalSpending = row[spendingAlias]?.asCurrency() ?: ZERO,
                totalIncomeCredits = row[incomeAlias]?.asCurrency() ?: ZERO,
                numTransactions = row[numTransactions].toInt()
            )
        }
    }

    companion object {
        fun BankStatementsTable.classificationsJoin() = innerJoin(ClassificationsTable, { BankStatementsTable.classificationId }, { id })
            .innerJoin(FilesTable, { ClassificationsTable.fileId }, { id })
            .innerJoin(ClientsTable, { FilesTable.clientId }, { id })

        fun BankStatementsTable.joinTransactions() = classificationsJoin()
            .leftJoin(TransactionsTable, { BankStatementsTable.id }, { TransactionsTable.statementId })
    }
}