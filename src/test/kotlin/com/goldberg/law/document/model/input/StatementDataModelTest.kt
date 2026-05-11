package com.goldberg.law.document.model.input


import com.goldberg.law.document.model.AD
import com.goldberg.law.document.model.DF
import com.goldberg.law.document.model.StatementModelValues.newBatesStampTable
import com.goldberg.law.document.model.StatementModelValues.newClassificationWithPages
import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.StatementDataModel.Companion.toBankDocument
import com.goldberg.law.document.model.input.StatementDataModel.Keys
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.entity.EntityValues.DEFAULT_BATES_STAMP
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Nested
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import kotlin.test.Test

class StatementDataModelTest {
    @Nested
    inner class GetTransactionRecords {

        @Test
        fun `getTransactionRecords returns empty list when all tables are null`() {
            val model = newStatementModel()
            assertThat(model.getTransactionRecords()).isEmpty()
        }

        @Test
        fun `getTransactionRecords returns records from a single populated table`() {
            val record = TransactionTableDepositWithdrawalRecord(
                date = "4/3",
                checkNumber = null,
                description = "deposit",
                depositAmount = 100.asCurrency(),
                withdrawalAmount = null,
                page = 1,
            )
            val model = newStatementModel(
                transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(record)),
            )

            val results = model.getTransactionRecords()
            assertThat(results).hasSize(1)
            assertThat(results[0].description).isEqualTo("deposit")
            assertThat(results[0].amount).isEqualTo(100.asCurrency())
        }

        @Test
        fun `getTransactionRecords returns all records from a table with multiple entries`() {
            val records = listOf(
                TransactionTableCreditsRecord(date = "4/1", description = "credit 1", additions = 50.asCurrency(), page = 1),
                TransactionTableCreditsRecord(date = "4/2", description = "credit 2", additions = 75.asCurrency(), page = 1),
                TransactionTableCreditsRecord(date = "4/3", description = "credit 3", additions = 25.asCurrency(), page = 1),
            )
            val model = newStatementModel(
                transactionTableCredits = TransactionTableCredits(records),
            )

            val results = model.getTransactionRecords()
            assertThat(results).hasSize(3)
            assertThat(results.map { it.description }).containsExactly("credit 1", "credit 2", "credit 3")
        }

        @Test
        fun `getTransactionRecords flattens records from multiple populated tables`() {
            val depositRecord = TransactionTableDepositWithdrawalRecord(
                date = "4/1", checkNumber = null, description = "deposit",
                depositAmount = 100.asCurrency(), withdrawalAmount = null, page = 1,
            )
            val creditRecord = TransactionTableCreditsRecord(
                date = "4/2", description = "credit", additions = 50.asCurrency(), page = 1,
            )
            val checksRecord = TransactionTableChecksRecord(
                date = "4/3", number = 1001, amount = 200.asCurrency(), page = 1,
            )

            val model = newStatementModel(
                transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(depositRecord)),
                transactionTableCredits = TransactionTableCredits(listOf(creditRecord)),
                transactionTableChecks = TransactionTableChecks(listOf(checksRecord)),
            )

            val results = model.getTransactionRecords()
            assertThat(results).hasSize(3)
        }
    }

    @Nested
    inner class GetBatesStampMap {

        @Test
        fun `getBatesStampsMap returns empty map when batesStampsTable is null`() {
            val model = newStatementModel(batesStampsTable = null)
            assertThat(model.getBatesStampsMap()).isEmpty()
        }

        @Test
        fun `getBatesStampsMap maps single bates stamp to correct page`() {
            val classification = newClassificationWithPages(pages = setOf(5))
            val model = newStatementModel(
                classification = classification,
                batesStampsTable = newBatesStampTable(1 to "$DEFAULT_BATES_STAMP-1"),
            )

            // page 1 in the bates stamp table → pagesOrdered[0] = 5
            assertThat(model.getBatesStampsMap()).isEqualTo(mapOf(5 to "$DEFAULT_BATES_STAMP-1"))
        }

        @Test
        fun `getBatesStampsMap maps multiple bates stamps correctly`() {
            val classification = newClassificationWithPages(pages = setOf(2, 4))
            val model = newStatementModel(
                classification = classification,
                batesStampsTable = newBatesStampTable(1 to "AG-001", 2 to "AG-002"),
            )

            // pagesOrdered = [2, 4] → stamp page 1 maps to 2, stamp page 2 maps to 4
            assertThat(model.getBatesStampsMap()).isEqualTo(mapOf(2 to "AG-001", 4 to "AG-002"))
        }

        @Test
        fun `getBatesStampsMap resolves page indices through pagesOrdered for non-contiguous pages`() {
            val classification = newClassificationWithPages(pages = setOf(3, 7, 12))
            val model = newStatementModel(
                classification = classification,
                batesStampsTable = newBatesStampTable(1 to "AG-A", 2 to "AG-B", 3 to "AG-C"),
            )

            // pagesOrdered = [3, 7, 12]
            assertThat(model.getBatesStampsMap()).isEqualTo(
                mapOf(3 to "AG-A", 7 to "AG-B", 12 to "AG-C")
            )
        }
    }

    @Nested
    inner class ToBankDocument {

        @Test
        fun `toBankDocument for empty map`() {
            val model = AD(emptyMap(), documentType = null).create().toBankDocument(newClassification())
            assertThat(model).isEqualTo(StatementDataModel(
                documentType = null,
                date = null,
                accountNumber = null,
                beginningBalance = null,
                endingBalance = null,
                feesCharged = null,
                interestCharged = null,
                summaryOfAccountsTable = null,
                transactionTableDepositWithdrawal = null,
                transactionTableAmount = null,
                transactionTableCreditsCharges = null,
                transactionTableDebits = null,
                transactionTableCredits = null,
                transactionTableChecks = null,
                batesStampsTable = null,
                classification = newClassification(),
            ))
        }

        @Test
        fun `toBankDocument parses beginning and ending balance`() {
            val model = AD(mapOf(
                Keys.BEGINNING_BALANCE to DF.of(1000.50, content = "$1,000.50"),
                Keys.ENDING_BALANCE to DF.of(750.25, content = "$750.25"),
            )).create().toBankDocument(newClassification())

            assertThat(model.beginningBalance).isEqualTo(1000.50.asCurrency())
            assertThat(model.endingBalance).isEqualTo(750.25.asCurrency())
        }

        @Test
        fun `toBankDocument parses fees and interest as positive values`() {
            val model = AD(mapOf(
                Keys.FEES_CHARGED to DF.of(-15.00, content = "-$15.00"),
                Keys.INTEREST_CHARGED to DF.of(-3.50, content = "-$3.50"),
            )).create().toBankDocument(newClassification())

            // positiveCurrencyValue takes abs()
            assertThat(model.feesCharged).isEqualTo(15.00.asCurrency())
            assertThat(model.interestCharged).isEqualTo(3.50.asCurrency())
        }

        // -- all fields together --

        @Test
        fun `toBankDocument populates all fields from a complete document`() {
            val accountNumber = "9876543210"
            val classification = newClassification()
            val model = AD(mapOf(
                Keys.STATEMENT_DATE to DF.of("01/15/2024"),
                Keys.ACCOUNT_NUMBER to DF.of(accountNumber),
                Keys.BEGINNING_BALANCE to DF.of(5000.00, content = "$5,000.00"),
                Keys.ENDING_BALANCE to DF.of(4500.00, content = "$4,500.00"),
                Keys.FEES_CHARGED to DF.of(25.00, content = "$25.00"),
                Keys.INTEREST_CHARGED to DF.of(10.00, content = "$10.00"),
                // SummaryOfAccounts table
                Keys.ACCOUNT_SUMMARY_TABLE to DF.of(
                    DF.of(
                        SummaryOfAccountsTableRecord.Keys.ACCOUNT_NUMBER to DF.of(accountNumber),
                        SummaryOfAccountsTableRecord.Keys.BEGINNING_BALANCE to DF.of(5000.00, content = "$5,000.00"),
                        SummaryOfAccountsTableRecord.Keys.ENDING_BALANCE to DF.of(4500.00, content = "$4,500.00"),
                    ),
                ),
                // DepositWithdrawal table
                Keys.TRANSACTION_TABLE_DEPOSIT_WITHDRAWAL to DF.of(
                    DF.of(
                        TransactionTableDepositWithdrawalRecord.Keys.DATE to DF.of("1/5", page = 1),
                        TransactionTableDepositWithdrawalRecord.Keys.DESCRIPTION to DF.of("payroll deposit"),
                        TransactionTableDepositWithdrawalRecord.Keys.DEPOSITS_ADDITIONS to DF.of(2000.00, content = "$2,000.00"),
                    ),
                ),
                // Amount table (credit card style)
                Keys.TRANSACTION_TABLE_AMOUNT to DF.of(
                    DF.of(
                        TransactionTableAmountRecord.Keys.DATE to DF.of("1/10", page = 1),
                        TransactionTableAmountRecord.Keys.DESCRIPTION to DF.of("Amazon purchase"),
                        TransactionTableAmountRecord.Keys.AMOUNT to DF.of(49.99, content = "$49.99"),
                    ),
                ),
                // CreditsCharges table (WF credit card style)
                Keys.TRANSACTION_TABLE_CREDITS_CHARGES to DF.of(
                    DF.of(
                        TransactionTableCreditsChargesRecord.Keys.DATE to DF.of("1/12", page = 1),
                        TransactionTableCreditsChargesRecord.Keys.DESCRIPTION to DF.of("payment received"),
                        TransactionTableCreditsChargesRecord.Keys.CREDITS to DF.of(500.00, content = "$500.00"),
                    ),
                ),
                // Debits table
                Keys.TRANSACTION_TABLE_DEBITS to DF.of(
                    DF.of(
                        TransactionTableDebitsRecord.Keys.DATE to DF.of("1/15", page = 1),
                        TransactionTableDebitsRecord.Keys.DESCRIPTION to DF.of("electric bill"),
                        TransactionTableDebitsRecord.Keys.SUBTRACTIONS to DF.of(150.00, content = "$150.00"),
                    ),
                ),
                // Credits table
                Keys.TRANSACTION_TABLE_CREDITS to DF.of(
                    DF.of(
                        TransactionTableCreditsRecord.Keys.DATE to DF.of("1/18", page = 1),
                        TransactionTableCreditsRecord.Keys.DESCRIPTION to DF.of("refund"),
                        TransactionTableCreditsRecord.Keys.ADDITIONS to DF.of(25.00, content = "$25.00"),
                    ),
                ),
                // Checks table
                Keys.TRANSACTION_TABLE_CHECKS to DF.of(
                    DF.of(
                        TransactionTableChecksRecord.Keys.DATE to DF.of("1/20", page = 1),
                        TransactionTableChecksRecord.Keys.NUMBER to DF.of(1001L),
                        TransactionTableChecksRecord.Keys.AMOUNT to DF.of(300.00, content = "$300.00"),
                    ),
                ),
                // Bates stamps
                Keys.BATES_STAMPS to DF.of(
                    DF.of(
                        BatesStampTableRow.Keys.VAL to DF.of("AG-00100", page = 1),
                    ),
                ),
            ), documentType = "custom:bank:eagle").create().toBankDocument(classification)

            val expected = StatementDataModel(
                documentType = "custom:bank:eagle",
                date = "01/15/2024",
                accountNumber = accountNumber,
                beginningBalance = 5000.00.asCurrency(),
                endingBalance = 4500.00.asCurrency(),
                feesCharged = 25.00.asCurrency(),
                interestCharged = 10.00.asCurrency(),
                summaryOfAccountsTable = SummaryOfAccountsTable(listOf(
                    SummaryOfAccountsTableRecord(
                        accountNumber = accountNumber,
                        beginningBalance = 5000.00.asCurrency(),
                        endingBalance = 4500.00.asCurrency(),
                    ),
                )),
                transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(
                    TransactionTableDepositWithdrawalRecord(
                        date = "1/5", checkNumber = null, description = "payroll deposit",
                        depositAmount = 2000.00.asCurrency(), withdrawalAmount = null, page = 1,
                    ),
                )),
                transactionTableAmount = TransactionTableAmount(listOf(
                    TransactionTableAmountRecord(
                        date = "1/10", description = "Amazon purchase",
                        amount = 49.99.asCurrency(), page = 1,
                    ),
                )),
                transactionTableCreditsCharges = TransactionTableCreditsCharges(listOf(
                    TransactionTableCreditsChargesRecord(
                        date = "1/12", description = "payment received",
                        credits = 500.00.asCurrency(), charges = null, page = 1,
                    ),
                )),
                transactionTableDebits = TransactionTableDebits(listOf(
                    TransactionTableDebitsRecord(
                        date = "1/15", description = "electric bill",
                        subtractions = 150.00.asCurrency(), page = 1,
                    ),
                )),
                transactionTableCredits = TransactionTableCredits(listOf(
                    TransactionTableCreditsRecord(
                        date = "1/18", description = "refund",
                        additions = 25.00.asCurrency(), page = 1,
                    ),
                )),
                transactionTableChecks = TransactionTableChecks(listOf(
                    TransactionTableChecksRecord(
                        date = "1/20", number = 1001,
                        amount = 300.00.asCurrency(), page = 1,
                    ),
                )),
                batesStampsTable = BatesStampTable(listOf(
                    BatesStampTableRow("AG-00100", 1),
                )),
                classification = classification,
            )

            assertThat(model).usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*\\.id", ".*\\.logger")
                .isEqualTo(expected)
        }
    }
}