package com.goldberg.law.document

import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.SummaryOfAccountsTable
import com.goldberg.law.document.model.input.SummaryOfAccountsTableRecord
import com.goldberg.law.document.model.input.tables.BatesStampTable
import com.goldberg.law.document.model.input.tables.BatesStampTableRow
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawalRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.EntityValues.DEFAULT_BATES_STAMP
import com.goldberg.law.entity.EntityValues.DEFAULT_BEGINNING_BALANCE
import com.goldberg.law.entity.EntityValues.DEFAULT_DATE
import com.goldberg.law.entity.EntityValues.DEFAULT_ENDING_BALANCE
import com.goldberg.law.entity.EntityValues.DEFAULT_STATEMENT_DATE_STRING
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newStatement
import com.goldberg.law.entity.EntityValues.newStatementDetails
import com.goldberg.law.entity.EntityValues.newTransactionDetails
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.bd
import com.goldberg.law.verify.BankStatementVerifier
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Nested
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import org.mockito.Mock
import org.mockito.kotlin.*
import java.math.BigDecimal

class DocumentStatementCreatorTest {
    @Mock
    private val bankStatementVerifier: BankStatementVerifier = mock()
    private val statementCreator = DocumentStatementCreator(bankStatementVerifier)

    @BeforeEach
    fun setup() {
        whenever(bankStatementVerifier.getSuspiciousReasons(any(), any(), any())).thenReturn(emptyList())
    }

    @ParameterizedTest
    @CsvSource(
        "12/01/2023 - 12/31/2023, 2023-12-31",
        "01/01/2024 - 01/31/2024, 2024-01-31",
        "March 1 2024 - March 31 2024, 2024-03-31",
        "2023-12-31, 2023-12-31",
        "01/15/2024, 2024-01-15",
        "March 31 2024, 2024-03-31",
    )
    fun `extracts date with and without Citi format`(input: String, expectedRaw: String) {
        val model = newStatementModel(date = input)

        val result = statementCreator.createBankStatements(newClassification(), model)

        val statement = result.single()

        assertThat(statement.date).isEqualTo(expectedRaw)
        assertThat(statement.statementDetails.statementDate()).isNotNull()
    }

    @ParameterizedTest
    @CsvSource(
        "1234567890, 7890",
        "****1234, 1234",
        "12-3456-7890, 7890",
        "9876, 9876",
        "9876*@(*, 9876",
        "123, 123",
    )
    fun `extracts last 4 digits of account number`(input: String, expected: String) {
        val model = newStatementModel(accountNumber = input)

        val result = statementCreator.createBankStatements(newClassification(), model)
        val statement = result.single()

        assertThat(statement.accountNumber).isEqualTo(expected)
    }

    @Test
    fun `single statement - all fields mapped, transactions wired, verifier called`() {
        val suspiciousReasons = listOf("balance does not add up")
        whenever(bankStatementVerifier.getSuspiciousReasons(any(), any(), any())).thenReturn(suspiciousReasons)

        val model = newStatementModel(
            classification = newClassification(),
            date = DEFAULT_STATEMENT_DATE_STRING,
            beginningBalance = DEFAULT_BEGINNING_BALANCE,
            endingBalance = DEFAULT_ENDING_BALANCE,
            transactionTableDepositWithdrawal = makeTable(
                makeRecord("Deposit", BigDecimal("50.00")),
                makeRecord("Withdrawal", BigDecimal("-25.00")),
            ),
            batesStampsTable = BatesStampTable(listOf(BatesStampTableRow(DEFAULT_BATES_STAMP, 1)))
        )

        val result = statementCreator.createBankStatements(newClassification(), model)

        val transactionDetails = listOf(
            newTransactionDetails(description = "Deposit", amount = BigDecimal("50.00")),
            newTransactionDetails(description = "Withdrawal", amount = BigDecimal("-25.00")),
        )
        assertThat(result.single()).entityCompare()
            .isEqualTo(newStatement(
                classification = newClassification(),
                statementDetails = newStatementDetails(),
                suspiciousReasons = suspiciousReasons,
                transactions = transactionDetails
            ))

        val stmtCaptor = argumentCaptor<StatementDetails>()
        val txnCaptor = argumentCaptor<List<TransactionDetails>>()
        val classCaptor = argumentCaptor<Classification>()
        verify(bankStatementVerifier).getSuspiciousReasons(stmtCaptor.capture(), txnCaptor.capture(), classCaptor.capture())
        assertThat(stmtCaptor.firstValue).entityCompare().isEqualTo(newStatementDetails())
        assertThat(txnCaptor.firstValue).entityCompare().isEqualTo(transactionDetails)
        assertThat(classCaptor.firstValue).entityCompare().isEqualTo(newClassification())
    }

    @Test
    fun `single statement - null balances do not throw`() {
        val model = newStatementModel(classification = newClassification(), beginningBalance = null, endingBalance = null)

        val result = statementCreator.createBankStatements(newClassification(), model)

        assertThat(result).hasSize(1)
        assertThat(result.single().beginningBalance).isNull()
        assertThat(result.single().endingBalance).isNull()
    }

    @Nested
    inner class MultipleStatementsPath {
        private val classification = newClassification(type = DocumentType.BankTypes.NFCU_BANK)
        @Test
        fun `groups transactions by beginning balance records and maps account summary per statement`() {
            val (beginningBalance1, endingBalance1) = Pair(100.5.bd(), 200.bd())
            val (beginningBalance2, endingBalance2) = Pair(300.bd(), 400.bd())

            val model = newStatementModel(
                classification = classification,
                summaryOfAccountsTable = SummaryOfAccountsTable(listOf(
                    SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_1, beginningBalance1, endingBalance1),
                    SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_2, beginningBalance2, endingBalance2),
                )),
                transactionTableDepositWithdrawal = makeTable(
                    makeRecord("Beginning Balance", null),
                    makeRecord("Deposit A", 50.asCurrency()),
                    makeRecord("Beginning Balance", null),
                    makeRecord("Deposit B", 75.asCurrency()),
                    makeRecord("Deposit C", 25.asCurrency()),
                ),
                batesStampsTable = BatesStampTable(listOf(BatesStampTableRow(DEFAULT_BATES_STAMP, 1)))
            )

            val result = statementCreator.createBankStatements(classification, model)

            assertThat(result).hasSize(2)

            val stmtDetails1 = newStatementDetails(
                accountNumber = ACCOUNT_NUMBER_1,
                beginningBalance = beginningBalance1.asCurrency(),
                endingBalance = endingBalance1.asCurrency(),
            )
            val stmtDetails2 = newStatementDetails(
                accountNumber = ACCOUNT_NUMBER_2,
                beginningBalance = beginningBalance2.asCurrency(),
                endingBalance = endingBalance2.asCurrency(),
            )
            val transactionDetails1 = listOf(
                newTransactionDetails(description = "Deposit A", amount = 50.asCurrency()),
            )
            val transactionDetails2 = listOf(
                newTransactionDetails(description = "Deposit B", amount = 75.asCurrency()),
                newTransactionDetails(description = "Deposit C", amount = 25.asCurrency()),
            )

            assertThat(result[0]).entityCompare()
                .isEqualTo(newStatement(
                    classification = classification,
                    statementDetails = stmtDetails1,
                    suspiciousReasons = emptyList(),
                    transactions = transactionDetails1
                ))

            assertThat(result[1]).entityCompare()
                .isEqualTo(newStatement(
                    classification = classification,
                    statementDetails = stmtDetails2,
                    suspiciousReasons = emptyList(),
                    transactions = transactionDetails2
                ))

            val stmtCaptor = argumentCaptor<StatementDetails>()
            val txnCaptor = argumentCaptor<List<TransactionDetails>>()
            verify(bankStatementVerifier, times(2)).getSuspiciousReasons(stmtCaptor.capture(), txnCaptor.capture(), eq(classification))
            assertThat(stmtCaptor.allValues[0]).entityCompare().isEqualTo(stmtDetails1)
            assertThat(txnCaptor.allValues[0]).entityCompare().isEqualTo(transactionDetails1)
            assertThat(stmtCaptor.allValues[1]).entityCompare().isEqualTo(stmtDetails2)
            assertThat(txnCaptor.allValues[1]).entityCompare().isEqualTo(transactionDetails2)
        }

        @Test
        fun `transactions before first beginning balance record form their own group`() {
            val summaryTable = SummaryOfAccountsTable(listOf(
                SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_1, null, null),
                SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_2, null, null),
            ))
            val model = newStatementModel(
                classification = classification,
                summaryOfAccountsTable = summaryTable,
                transactionTableDepositWithdrawal = makeTable(
                    makeRecord("Early transaction", BigDecimal("10.00")),  // before any beginning balance
                    makeRecord("Beginning Balance", null),
                    makeRecord("Deposit B", BigDecimal("75.00")),
                )
            )

            val result = statementCreator.createBankStatements(classification, model)

            assertThat(result).hasSize(2)
            assertThat(result[0].transactions).hasSize(1)
            assertThat(result[0].transactions[0].description).isEqualTo("Early transaction")
            assertThat(result[1].transactions).hasSize(1)
            assertThat(result[1].transactions[0].description).isEqualTo("Deposit B")
        }

        @Test
        fun `zip truncates statements to whichever of transaction groups or account rows is smaller`() {
            // 3 transaction groups but only 2 account summary rows → 2 statements
            val summaryTable = SummaryOfAccountsTable(listOf(
                SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_1, null, null),
                SummaryOfAccountsTableRecord(ACCOUNT_NUMBER_2, null, null),
            ))
            val model = newStatementModel(
                classification = classification,
                summaryOfAccountsTable = summaryTable,
                transactionTableDepositWithdrawal = makeTable(
                    makeRecord("Beginning Balance", null),
                    makeRecord("Beginning Balance", null),
                    makeRecord("Beginning Balance", null),
                )
            )

            assertThat(statementCreator.createBankStatements(classification, model)).hasSize(2)
        }

        @Test
        fun `null summaryOfAccountsTable throws NullPointerException`() {
            val model = newStatementModel(
                classification = classification,
                summaryOfAccountsTable = null,
                transactionTableDepositWithdrawal = makeTable(makeRecord("Beginning Balance", null))
            )

            assertThatThrownBy { statementCreator.createBankStatements(classification, model) }
                .isInstanceOf(NullPointerException::class.java)
        }
    }

    @Nested
    inner class IsBeginningBalanceRecord {
        @Test
        fun `test beginning balance records`() {
            with(DocumentStatementCreator.Companion) {
                assertThat(newTransactionDetails(description = "Beginning Balance", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "Opening Balance", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "BEGINNING BALANCE", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "beginning balance", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "beginning bal", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "beginning bal.", amount = null).isBeginningBalanceRecord()).isTrue()
                assertThat(newTransactionDetails(description = "BEGINnING BaL.", amount = null).isBeginningBalanceRecord()).isTrue()
            }
        }

        @Test
        fun `non-null amount is still a beginning balance record`() {
            with(DocumentStatementCreator.Companion) {
                assertThat(newTransactionDetails(description = "Beginning Balance", amount = BigDecimal("100.00")).isBeginningBalanceRecord()).isTrue()
            }
        }

        @Test
        fun `unrelated description is not a beginning balance record`() {
            with(DocumentStatementCreator.Companion) {
                assertThat(newTransactionDetails(description = "Regular Deposit", amount = null).isBeginningBalanceRecord()).isFalse()
            }
        }
    }

    companion object {
        fun makeRecord(description: String?, amount: BigDecimal?, page: Int = 1) =
            TransactionTableDepositWithdrawalRecord(
                date = DEFAULT_DATE,
                checkNumber = null,
                description = description,
                depositAmount = amount?.takeIf { it >= BigDecimal.ZERO },
                withdrawalAmount = amount?.takeIf { it < BigDecimal.ZERO }?.negate(),
                page = page,
            )

        fun makeTable(vararg records: TransactionTableDepositWithdrawalRecord) =
            TransactionTableDepositWithdrawal(records.toList())

        const val ACCOUNT_NUMBER_1 = "1111"
        const val ACCOUNT_NUMBER_2 = "2222"
    }
}