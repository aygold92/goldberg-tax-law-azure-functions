package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.database.service.TransactionService
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.TransactionsTable
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.function.api.model.TransactionCheckMatch
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import com.goldberg.law.document.exception.EntityNotFoundException
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class TransactionServiceTest : DatabaseTest() {

    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val transactionService = TransactionService(db)
    private val statementService = StatementService(transactionService, bankStatementVerifier, db)

    private val classificationService = ClassificationService(db)
    private val clientService = ClientService(db)
    private val fileService = FileService(db)
    private val checkService = CheckService(db)

    // Shared upstream fixtures
    private lateinit var clientId: UUID
    private lateinit var classification: Classification
    private lateinit var statementId: UUID

    @BeforeAll
    fun setupFixtures() {
        clientId = clientService.insertClient(EntityValues.DEFAULT_CLIENT_NAME, UUID.randomUUID())
        val fileId = fileService.insertFile(
            EntityValues.newInputFile(client = EntityValues.newClient(clientId = clientId)),
            UUID.randomUUID(),
        )
        val infos = classificationService.insertClassifications(EntityValues.newClassifiedFile(fileId = fileId))
        classification = classificationService.loadClassification(infos.single().classificationId)
        statementId = statementService.insertBankStatementWithTransactions(
            EntityValues.newStatement(
                classification = classification,
                statementDetails = EntityValues.newStatementDetails(),
                transactions = emptyList(),
            )
        )
    }

    @AfterEach
    fun clearTransactions() {
        db.txnSafe {
            TransactionsTable.deleteAll()
            ChecksTable.deleteAll()
        }
    }

    @Nested
    inner class UpsertTransactions {

        @Test
        fun `inserts new transactions with all fields stored correctly`() {
            val txDetails = EntityValues.newTransactionDetails()

            transactionService.upsertTransactions(statementId, listOf(txDetails))

            val stored = transactionService.loadTransactions(statementId)
            assertThat(stored).hasSize(1)
            assertThat(stored.single()).entityCompare().isEqualTo(txDetails)
            assertThat(stored.single().transactionId).isEqualTo(txDetails.transactionId)
        }

        @Test
        fun `calling twice with the same transaction should update not double-insert`() {
            val txDetails = EntityValues.newTransactionDetails()
            transactionService.upsertTransactions(statementId, listOf(txDetails))
            transactionService.upsertTransactions(statementId, listOf(txDetails))

            val transactions = transactionService.loadTransactions(statementId)
            assertThat(transactions).hasSize(1)
            assertThat(transactions.single()).entityCompare().isEqualTo(txDetails)
        }
    }

    @Nested
    inner class DeleteTransactions {

        @Test
        fun `removes the specified transactions`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = transactionService.loadTransactions(statementId).single().transactionId

            transactionService.deleteTransactions(listOf(txId))

            assertThat(transactionService.loadTransactions(statementId)).isEmpty()
        }

        @Test
        fun `empty list deletes nothing`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))

            transactionService.deleteTransactions(emptyList())

            assertThat(transactionService.loadTransactions(statementId)).hasSize(1)
        }
    }

    @Nested
    inner class FindMatchingChecks {

        @Test
        fun `returns transactions whose checkNumber and accountNumber match an unlinked check`() {
            val checkNumber = EntityValues.DEFAULT_CHECK_NUMBER
            checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = checkNumber),
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
                EntityValues.newCheckDetails(checkNumber = checkNumber),
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
                EntityValues.newCheckDetails(),
            )
            transactionService.upsertTransactions(
                statementId,
                listOf(EntityValues.newTransactionDetails(checkNumber = EntityValues.DEFAULT_CHECK_NUMBER)),
            )
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())

            assertThat(transactionService.findMatchingChecks(otherClientId)).isEmpty()
        }

        @Test
        fun `does not match when check account number differs from statement account number`() {
            val checkNumber = EntityValues.DEFAULT_CHECK_NUMBER
            checkService.insertCheck(
                classification,
                EntityValues.newCheckDetails(checkNumber = checkNumber, accountNumber = "9999"),
            )
            transactionService.upsertTransactions(
                statementId,
                listOf(EntityValues.newTransactionDetails(checkNumber = checkNumber)),
            )

            // Statement accountNumber is DEFAULT_ACCOUNT_NUMBER ("1234"), check is "9999" — no match
            assertThat(transactionService.findMatchingChecks(clientId)).isEmpty()
        }
    }

    @Nested
    inner class LoadTransaction {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy { transactionService.loadTransaction(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class Timing {

        @Test
        fun `upsert sets createdAt and updatedAt on insert, re-upsert only advances updatedAt`() {
            Thread.sleep(20)
            val txDetails = EntityValues.newTransactionDetails()
            transactionService.upsertTransactions(statementId, listOf(txDetails))

            Thread.sleep(20)
            val loaded = transactionService.loadTransaction(txDetails.transactionId)
            val creationTime = loaded.createdAt
            assertTimeIsDuringTest(creationTime)
            assertThat(loaded.updatedAt).isEqualTo(creationTime)

            Thread.sleep(20)
            transactionService.upsertTransactions(statementId, listOf(txDetails))

            val afterUpdate = transactionService.loadTransaction(txDetails.transactionId)
            assertTimeInWindow(afterUpdate.updatedAt, creationTime)
            assertThat(afterUpdate.createdAt).isEqualTo(creationTime)
        }
    }

    @Nested
    inner class LoadTransactionsByStatement {

        @Test
        fun `empty list returns empty map`() {
            assertThat(transactionService.loadTransactionsByStatement(emptyList())).isEmpty()
        }

        @Test
        fun `statement with no transactions is absent from result map`() {
            val result = transactionService.loadTransactionsByStatement(listOf(statementId))
            assertThat(result[statementId]).isNullOrEmpty()
        }

        @Test
        fun `returns transactions grouped by statementId`() {
            val stmt2Id = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(classification = classification, transactions = emptyList())
            )
            val tx1 = EntityValues.newTransactionDetails()
            val tx2 = EntityValues.newTransactionDetails(transactionId = UUID.randomUUID())
            transactionService.upsertTransactions(statementId, listOf(tx1))
            transactionService.upsertTransactions(stmt2Id, listOf(tx2))

            val result = transactionService.loadTransactionsByStatement(listOf(statementId, stmt2Id))

            assertThat(result[statementId]).hasSize(1)
            assertThat(result[statementId]!!.single()).entityCompare().isEqualTo(tx1)
            assertThat(result[stmt2Id]).hasSize(1)
            assertThat(result[stmt2Id]!!.single()).entityCompare().isEqualTo(tx2)
        }

        @Test
        fun `does not return transactions for statements not in the input list`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))

            val result = transactionService.loadTransactionsByStatement(listOf(UUID.randomUUID()))

            assertThat(result).doesNotContainKey(statementId)
        }
    }

    @Nested
    inner class LinkTransactionsWithChecks {

        @Test
        fun `sets the checkId on the specified transactions`() {
            val checkId = checkService.insertCheck(classification, EntityValues.newCheckDetails())
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = transactionService.loadTransactions(statementId).single().transactionId

            transactionService.linkTransactionsWithChecks(listOf(TransactionCheckMatch(txId, checkId)))

            val linked = transactionService.loadTransaction(txId)
            assertThat(linked.checkId).isEqualTo(checkId)
        }

        @Test
        fun `empty list does nothing`() {
            transactionService.upsertTransactions(statementId, listOf(EntityValues.newTransactionDetails()))
            val txId = transactionService.loadTransactions(statementId).single().transactionId

            transactionService.linkTransactionsWithChecks(emptyList())

            assertThat(transactionService.loadTransaction(txId).checkId).isNull()
        }
    }
}
