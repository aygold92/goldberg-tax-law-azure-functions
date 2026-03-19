package com.goldberg.law.database.services

import com.goldberg.law.database.DatabaseTest
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.ClientService
import com.goldberg.law.database.service.FileService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues
import com.goldberg.law.verify.BankStatementVerifier
import com.goldberg.law.verify.TransactionVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import java.util.UUID

class StatementServiceTest : DatabaseTest() {

    private val transactionVerifier = TransactionVerifier()
    private val bankStatementVerifier = BankStatementVerifier(transactionVerifier)
    private val statementService = StatementService(bankStatementVerifier, db)
    private val classificationService = ClassificationService(statementService, db)

    private val clientService = ClientService(db)
    private val fileService = FileService(db)

    // BankStatementsTable.accountNumber is CHAR(4), which is too narrow for real account numbers.
    // EntityValues.DEFAULT_ACCOUNT_NUMBER ("1234567890") is 10 chars and would be truncated.
    // Most tests use "1234" so inserts succeed and the logic under test can be exercised.
    // A dedicated test in InsertStatement documents the column width bug.
    private val testAccountNumber = "1234"

    // Shared upstream fixtures — recreated before each test.
    // insertBankStatementWithTransactions is under test here, so it must NOT be called in @BeforeEach.
    private lateinit var clientId: UUID
    private lateinit var classification: Classification

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
    }

    @Nested
    inner class FullLifecycle {

        @Test
        fun `insert, load, list, update, then delete`() {
            val txDetails = EntityValues.newTransactionDetails()
            val statementDetails = EntityValues.newStatementDetails(accountNumber = testAccountNumber)
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = statementDetails,
                    transactions = listOf(txDetails),
                )
            )

            // loadStatement — NOTE: missing a ClientsTable join.
            // The query joins BankStatements → Classifications → Files but not → Clients.
            // Classification.fromRow → InputFile.fromRow → Client.fromRow reads ClientsTable.id
            // which is absent from the result set. This test will fail until the join is added.
            val loaded = statementService.loadStatement(statementId)
            assertThat(loaded.statementId).isEqualTo(statementId)
            assertThat(loaded.classificationId).isEqualTo(classification.classificationId)
            assertThat(loaded.accountNumber).isEqualTo(testAccountNumber)
            assertThat(loaded.date).isEqualTo(statementDetails.date)
            assertThat(loaded.beginningBalance).isEqualByComparingTo(statementDetails.beginningBalance)
            assertThat(loaded.endingBalance).isEqualByComparingTo(statementDetails.endingBalance)
            assertThat(loaded.batesStamps).isEqualTo(statementDetails.batesStamps)
            assertThat(loaded.transactions).hasSize(1)
            assertThat(loaded.transactions.single())
                .usingRecursiveComparison()
                .ignoringFields("transactionId")
                .isEqualTo(txDetails)

            // listStatements — verify summary appears with correct fields
            val summaries = statementService.listStatements(clientId)
            assertThat(summaries).hasSize(1)
            val summary = summaries.single()
            assertThat(summary.statementId).isEqualTo(statementId)
            assertThat(summary.accountNumber).isEqualTo(testAccountNumber)
            assertThat(summary.numTransactions).isEqualTo(1)

            // Update
            val updatedDetails = EntityValues.newStatementDetails(
                statementId = statementId,
                accountNumber = "5678",
                date = "02/28/2024",
                beginningBalance = 2000.toBigDecimal(),
                endingBalance = 1500.toBigDecimal(),
                batesStamps = mapOf(2 to "AG-99999"),
            )
            statementService.updateStatement(updatedDetails)

            // loadStatement after update — verify all fields reflect the change
            val afterUpdate = statementService.loadStatement(statementId)
            assertThat(afterUpdate.accountNumber).isEqualTo("5678")
            assertThat(afterUpdate.date).isEqualTo("02/28/2024")
            assertThat(afterUpdate.beginningBalance).isEqualByComparingTo(2000.toBigDecimal())
            assertThat(afterUpdate.endingBalance).isEqualByComparingTo(1500.toBigDecimal())
            assertThat(afterUpdate.batesStamps).isEqualTo(mapOf(2 to "AG-99999"))

            // listStatements after update
            val summariesAfterUpdate = statementService.listStatements(clientId)
            assertThat(summariesAfterUpdate).hasSize(1)
            assertThat(summariesAfterUpdate.single().accountNumber).isEqualTo("5678")

            // Delete
            statementService.deleteBankStatementWithData(statementId)

            // loadStatement after delete throws
            assertThatThrownBy { statementService.loadStatement(statementId) }
                .isInstanceOf(EntityNotFoundException::class.java)

            // listStatements after delete is empty
            assertThat(statementService.listStatements(clientId)).isEmpty()
        }
    }

    @Nested
    inner class InsertStatement {

        @Test
        fun `statement with no transactions inserts successfully`() {
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(accountNumber = testAccountNumber),
                    transactions = emptyList(),
                )
            )
            assertThat(statementId).isNotNull()
        }

        @Test
        fun `account number should support full-length values`() {
            // NOTE: BankStatementsTable.accountNumber is CHAR(4) which is too restrictive.
            // Real account numbers are typically 10–17 digits. This test documents the expected
            // behaviour — full account numbers should be accepted without truncation.
            // This test will fail until the column type is widened (e.g. VARCHAR(20)).
            val statementId = statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(
                        accountNumber = EntityValues.DEFAULT_ACCOUNT_NUMBER, // "1234567890" — 10 chars
                    ),
                    transactions = emptyList(),
                )
            )
            assertThat(statementId).isNotNull()
            // Verify it round-trips correctly — if truncated, this would not equal the input
            val loaded = statementService.loadStatement(statementId)
            assertThat(loaded.accountNumber).isEqualTo(EntityValues.DEFAULT_ACCOUNT_NUMBER)
        }
    }

    @Nested
    inner class LoadStatement {

        @Test
        fun `unknown ID throws EntityNotFoundException`() {
            assertThatThrownBy { statementService.loadStatement(UUID.randomUUID()) }
                .isInstanceOf(EntityNotFoundException::class.java)
        }
    }

    @Nested
    inner class ListStatements {

        @Test
        fun `returns correct transaction count and spending and income aggregations`() {
            val income = EntityValues.newTransactionDetails(amount = 1000.0.toBigDecimal())
            val spending = EntityValues.newTransactionDetails(amount = (-300.0).toBigDecimal())
            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(
                        accountNumber = testAccountNumber,
                        beginningBalance = 1000.0.toBigDecimal(),
                        endingBalance = 1700.0.toBigDecimal(),
                    ),
                    transactions = listOf(income, spending),
                )
            )

            val summaries = statementService.listStatements(clientId)
            assertThat(summaries).hasSize(1)
            val summary = summaries.single()
            assertThat(summary.numTransactions).isEqualTo(2)
            assertThat(summary.totalSpending).isEqualByComparingTo((-300.0).toBigDecimal())

            // NOTE: listStatements has a bug — both the totalSpending and totalIncome aggregation
            // expressions are aliased "totalSpending". When Exposed reads row[totalIncome] it
            // returns the totalSpending value instead of the real income sum.
            // This assertion will fail until the totalIncome alias is given a distinct name.
            assertThat(summary.totalIncomeCredits).isEqualByComparingTo(1000.0.toBigDecimal())
        }

        @Test
        fun `does not return statements belonging to other clients`() {
            statementService.insertBankStatementWithTransactions(
                EntityValues.newStatement(
                    classification = classification,
                    statementDetails = EntityValues.newStatementDetails(accountNumber = testAccountNumber),
                    transactions = emptyList(),
                )
            )
            val otherClientId = clientService.insertClient("Other Client", UUID.randomUUID())

            assertThat(statementService.listStatements(otherClientId)).isEmpty()
        }
    }
}
