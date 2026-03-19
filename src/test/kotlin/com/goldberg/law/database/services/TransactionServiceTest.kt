package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.database.tables.TransactionsTable
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TransactionServiceTest : DatabaseTest() {

    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val statementService = StatementService(bankStatementVerifier, db)
    private val classificationService = ClassificationService(statementService, db)

    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val checkService = CheckService(db)
    private val transactionService = TransactionService(db)

    // BankStatementsTable.accountNumber is CHAR(4) — see StatementServiceTest for details.
    private val testAccountNumber = "1234"

    // Shared upstream fixtures — recreated before each test.
    // upsertTransactions is under test here, so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID
    private lateinit var classification: Classification
    private lateinit var statementId: UUID

    @BeforeEach
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        val fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
            UUID.randomUUID(),
        )
        val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
        classification = classificationService.loadClassification(infos.single().classificationId)
        statementId = statementService.insertBankStatementWithTransactions(
            EntityValues.newStatement(
                classification = classification,
                statementDetails = EntityValues.newStatementDetails(accountNumber = testAccountNumber),
                transactions = emptyList(),
            )
        )
    }

    // --- Direct table helpers ---
    // TransactionService has no loadTransactions method, so direct table access is used here
    // for post-upsert verification. A loadTransactions(statementId) service method would
    // make these helpers unnecessary.

    private fun loadTransactions(statementId: UUID): List<TransactionDetails> = transaction(db) {
        TransactionsTable
            .select(TransactionsTable.statementId eq statementId)
            .map { TransactionDetails.fromRow(it) }
    }

    private fun loadTransaction(txId: UUID): TransactionDetails = transaction(db) {
        TransactionsTable
            .select(TransactionsTable.id eq txId)
            .map { TransactionDetails.fromRow(it) }
            .single()
    }

    @Nested
    inner class UpsertTransactions {

        @Test
        fun `inserts new transactions with all fields stored correctly`() {
            val txDetails = EntityValues.newTransactionDetails()

            // NOTE: batchUpsert never sets this[TransactionsTable.id], so every call
            // auto-generates a new UUID — effectively always inserting, never updating.
            // Fix: set this[TransactionsTable.id] = transaction.transactionId.
            transactionService.upsertTransactions(statementId, listOf(txDetails))

            val stored = loadTransactions(statementId)
            assertThat(stored).hasSize(1)
            // transactionId is auto-generated (bug: should use txDetails.transactionId)
            assertThat(stored.single())
                .usingRecursiveComparison()
                .ignoringFields("transactionId")
                .isEqualTo(txDetails)
        }

        @Test
        fun `calling twice with the same transaction should update not double-insert`() {
            // NOTE: because batchUpsert never sets this[TransactionsTable.id], each call
            // inserts a new row rather than updating the existing one. This test documents
            // the expected idempotent behaviour and will fail until transactionId is set
            // in the batchUpsert body.
            val txDetails = EntityValues.newTransactionDetails()
            transactionService.upsertTransactions(statementId, listOf(txDetails))
            transactionService.upsertTransactions(statementId, listOf(txDetails))

            assertThat(loadTransactions(statementId)).hasSize(1)
        }
    }

    @Nested
    inner class DeleteTransactions {

        @Test
        fun `removes the specified transactions`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = loadTransactions(statementId).single().transactionId

            transactionService.deleteTransactions(listOf(txId))

            assertThat(loadTransactions(statementId)).isEmpty()
        }

        @Test
        fun `empty list deletes nothing`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))

            transactionService.deleteTransactions(emptyList())

            assertThat(loadTransactions(statementId)).hasSize(1)
        }
    }

    @Nested
    inner class FindMatchingChecks {

        @Test
        fun `returns transactions whose checkNumber and accountNumber match an unlinked check`() {
            val checkNumber = EntityValues.DEFAULT_CHECK_NUMBER
            checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = checkNumber, accountNumber = testAccountNumber),
            )
            transactionService.upsertTransactions(
                statementId,
                listOf(EntityValues.newTransactionDetails(checkNumber = checkNumber, checkId = null)),
            )

            val matches = transactionService.findMatchingChecks(clientId)

            assertThat(matches).hasSize(1)
            assertThat(matches.single().checkNumber).isEqualTo(checkNumber)
        }

        @Test
        fun `does not return transactions already linked to a check`() {
            val checkNumber = EntityValues.DEFAULT_CHECK_NUMBER
            val checkId = checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = checkNumber, accountNumber = testAccountNumber),
            )
            transactionService.upsertTransactions(
                statementId,
                listOf(EntityValues.newTransactionDetails(checkNumber = checkNumber, checkId = checkId)),
            )

            assertThat(transactionService.findMatchingChecks(clientId)).isEmpty()
        }

        @Test
        fun `does not return transactions belonging to other clients`() {
            checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(accountNumber = testAccountNumber),
            )
            transactionService.upsertTransactions(
                statementId,
                listOf(EntityValues.newTransactionDetails(checkNumber = EntityValues.DEFAULT_CHECK_NUMBER)),
            )
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())

            assertThat(transactionService.findMatchingChecks(otherClientId)).isEmpty()
        }
    }

    @Nested
    inner class LinkTransactionsWithChecks {

        @Test
        fun `sets the checkId on the specified transactions`() {
            val checkId = checkService.insertCheck(classification, EntityValues.newCheckDetails())
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = loadTransactions(statementId).single().transactionId

            transactionService.linkTransactionsWithChecks(listOf(TransactionCheckMatch(txId, checkId)))

            val linked = loadTransaction(txId)
            assertThat(linked.checkId).isEqualTo(checkId)
        }

        @Test
        fun `empty list does nothing`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = loadTransactions(statementId).single().transactionId

            transactionService.linkTransactionsWithChecks(emptyList())

            assertThat(loadTransaction(txId).checkId).isNull()
        }
    }
}
