package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_CHECK_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CheckToStatementMatcherTest {
    private val checkToStatementMatcher = CheckToStatementMatcher()
    @Test
    fun testAccountNumbersOnStatementAreBlankedOut() {
        val otherAccountNumber = "xxxxxxxxx7890"
        val bankStatement1 = statementWithRecords(BASIC_TH_RECORD).copy(accountNumber = otherAccountNumber)
        val bankStatement2 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1000)).copy(accountNumber = otherAccountNumber)
        val bankStatement3 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1001)).copy(accountNumber = otherAccountNumber)


        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement1, bankStatement2, bankStatement3), ALL_CHECKS_LIST
        )

        val bankStatement2CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1000)).copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))
        val bankStatement3CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1001)).copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1001 to BASIC_CHECK_PAGE_METADATA))
        val finalStatementList = listOf(bankStatement1, bankStatement2CheckData, bankStatement3CheckData)
        assertThat(finalStatements).isEqualTo(finalStatementList)
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(ALL_CHECK_KEYS)).isEmpty()
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdButNumbersMatch() {
        // this test should still pass because the number, date, etc. match
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement1 = statementWithRecords(BASIC_TH_RECORD).copy(accountNumber = otherAccountNumber)
        val bankStatement2 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1000))
            .copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))
        val bankStatement3 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1001))
            .copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1001 to BASIC_CHECK_PAGE_METADATA))

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement1, bankStatement2, bankStatement3), ALL_CHECKS_LIST
        )

        val bankStatement2CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1000)).copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))
        val bankStatement3CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1001)).copy(accountNumber = otherAccountNumber, checks = mutableMapOf(1001 to BASIC_CHECK_PAGE_METADATA))
        val finalStatementList = listOf(bankStatement1, bankStatement2CheckData, bankStatement3CheckData)
        assertThat(finalStatements).isEqualTo(finalStatementList)
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(ALL_CHECK_KEYS)).isEmpty()
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdDateDoesNotMatch() {
        // we only check that the check number matches
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement = newBankStatement(accountNumber = otherAccountNumber)
            .update(transactions = listOf(newHistoryRecord(date = "6 10 2020", checkNumber = 1000, checkData = newCheckData(1000))))
            .copy(checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))


        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), listOf(newCheckData(1000))
        )

        assertThat(finalStatements).isEqualTo(listOf(bankStatement))
        assertThat(finalStatements.getMissingChecks()).isEqualTo(emptySet<CheckDataKey>())
//        assertThat(finalStatements.getMissingChecks()).isEqualTo(setOf(CheckDataKey(otherAccountNumber, 1000)))
//        assertThat(finalStatements.getChecksNotUsed(setOf(CHECK_DATA_KEY_1000))).isEqualTo(setOf(CHECK_DATA_KEY_1000))
        assertThat(finalStatements.getChecksNotUsed(setOf(CHECK_DATA_KEY_1000))).isEqualTo(setOf<CheckDataKey>())
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdAmountDoesNotMatch() {
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement = newBankStatement(accountNumber = otherAccountNumber)
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000)))


        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), listOf(newCheckData(1000))
        )

        assertThat(finalStatements).isEqualTo(listOf(bankStatement))
        assertThat(finalStatements.getMissingChecks()).isEqualTo(setOf(CheckDataKey(otherAccountNumber, 1000)))
        assertThat(finalStatements.getChecksNotUsed(setOf(CHECK_DATA_KEY_1000))).isEqualTo(setOf(CHECK_DATA_KEY_1000))
    }

    @Test
    fun testAccountNumbersMatchButOtherStuffDoesNot() {
        // this should match because it's most likely that the record wasn't read the record correctly
        val bankStatement = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000)))


        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), listOf(newCheckData(1000))
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000, checkData = newCheckData(1000))))
            .copy(checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(setOf(CHECK_DATA_KEY_1000))).isEmpty()
    }

    // TODO: test cases where account mapping is weird

    @Test
    fun testAccountNumberMatchesMultiple() {
        val statementAccountNumber = "xxx7890"
        val otherAccountNumber = "567890"
        val checkData1 = newCheckData(1000, otherAccountNumber)
        val checkData2 = newCheckData(1001)

        val statement = statementWithRecords(newHistoryRecord(checkNumber = 1000)).copy(accountNumber = statementAccountNumber)

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement), listOf(checkData1, checkData2)
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(setOf(checkData1.checkDataKey, checkData2.checkDataKey))).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testAccountNumberMatchesNone() {
        // TODO: not sure if this should happen or not
        val statementAccountNumber = "xxx78902"
        val otherAccountNumber = "567890"
        val checkData1 = newCheckData(1000, otherAccountNumber)
        val checkData2 = newCheckData(1001)

        val statement = statementWithRecords(newHistoryRecord(checkNumber = 1000)).copy(accountNumber = statementAccountNumber)

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement), listOf(checkData1, checkData2)
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(setOf(checkData1.checkDataKey, checkData2.checkDataKey))).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testMultipleCheckNumbersDifferentAccountsMatch() {
        // TODO: this is so unlikely it's probably fine that this adds the same check twice
        val statementAccountNumber = "xxx7890"
        val otherAccountNumber = "567890"
        val checkData1 = newCheckData(1000, otherAccountNumber)
        val checkData2 = newCheckData(1001)

        val statement1 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000)
        )

        val statement2 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000)
        ).copy(accountNumber = statementAccountNumber)

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement1, statement2), listOf(checkData1, checkData2)
        )

        val bankStatement1WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))
        val bankStatement2WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(bankStatement1WithCheckData, bankStatement2WithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(setOf(checkData1.checkDataKey, checkData2.checkDataKey))).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testMultipleCheckNumbersDifferentAccountsDifferentData() {
        val statementAccountNumber = "xxx7890"
        val otherAccountNumber = "567890"
        val checkData1 = CheckDataModel(
            otherAccountNumber,
            1000,
            "Some Guy",
            "test",
            normalizeDate("4 10 2020"),
            1500.asCurrency(),
            null,
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )
        val checkData2 = newCheckData(1001)

        val statement1 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000)
        )

        val statement2 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000, amount = 1500.0, date = "4 10 2020")
        ).copy(accountNumber = statementAccountNumber)

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement1, statement2), listOf(checkData1, checkData2)
        )

        val bankStatement2WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, date = "4 10 2020", checkNumber = 1000, checkData = checkData1)))
            .copy(accountNumber = statementAccountNumber, checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(statement1, bankStatement2WithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEqualTo(setOf(CHECK_DATA_KEY_1000))
        assertThat(finalStatements.getChecksNotUsed(setOf(checkData1.checkDataKey, checkData2.checkDataKey))).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testExtractCheckEntries() {
        val compositeCheckData = CheckDataModel(
            ACCOUNT_NUMBER,
            null,
            null,
            null,
            null,
            null,
            CheckEntriesTable(images = listOf(
                CheckEntriesTableRow(
                    normalizeDate("4 10 2020"),
                    1000,
                    "Some Guy",
                    "test",
                    1500.asCurrency(),
                    null
                ),
                CheckEntriesTableRow(
                    normalizeDate("4 15 2020"),
                    1001,
                    "Another Guy",
                    "desc",
                    2000.asCurrency(),
                    null
                ),
            )),
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )
        val checkData1 = CheckDataModel(
            ACCOUNT_NUMBER,
            1000,
            "Some Guy",
            "test",
            normalizeDate("4 10 2020"),
            1500.asCurrency(),
            null,
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )
        val checkData2 = CheckDataModel(
            ACCOUNT_NUMBER,
            1001,
            "Another Guy",
            "desc",
            normalizeDate("4 15 2020"),
            2000.asCurrency(),
            null,
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )

        val statement1 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000, amount = 1500.0, date = normalizeDate("4 10 2020"))
        )

        val statement2 = statementWithRecords(
            newHistoryRecord(checkNumber = 1001, amount = 2000.0, date = "4 15 2020")
        )

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement1, statement2), listOf(compositeCheckData).flatMap { it.extractNestedChecks() }
        )

        val bankStatement1WithCheckData = statementWithRecords(
            newHistoryRecord(amount = 1500.0, date = "4 10 2020", checkNumber = 1000, checkData = checkData1)
        ).copy(checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA))

        val bankStatement2WithCheckData = statementWithRecords(
            newHistoryRecord(amount = 2000.0, date = "4 15 2020", checkNumber = 1001, checkData = checkData2)
        ).copy(checks = mutableMapOf(1001 to BASIC_CHECK_PAGE_METADATA))

        assertThat(finalStatements).isEqualTo(listOf(bankStatement1WithCheckData, bankStatement2WithCheckData))
        assertThat(finalStatements.getMissingChecks()).isEqualTo(emptySet<CheckDataModel>())
        assertThat(finalStatements.getChecksNotUsed(setOf(checkData1.checkDataKey, checkData2.checkDataKey))).isEqualTo(emptySet<CheckDataModel>())
    }

    @Test
    fun testTwoChecksSameStatement() {
        val bankStatement = statementWithRecords(
            newHistoryRecord(checkNumber = 1000),
            newHistoryRecord(checkNumber = 1001)
        )

        val finalStatements = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), ALL_CHECKS_LIST
        )

        val bankStatementCheckData = statementWithRecords(
            historyRecordWithCheckData(1000),
            historyRecordWithCheckData(1001)
        ).copy(checks = mutableMapOf(1000 to BASIC_CHECK_PAGE_METADATA, 1001 to BASIC_CHECK_PAGE_METADATA))
        val finalStatementList = listOf(bankStatementCheckData)
        assertThat(finalStatements).isEqualTo(finalStatementList)
        assertThat(finalStatements.getMissingChecks()).isEmpty()
        assertThat(finalStatements.getChecksNotUsed(ALL_CHECK_KEYS)).isEmpty()
    }

    companion object {
        val CHECK_DATA_KEY_1000 = CheckDataKey(ACCOUNT_NUMBER, 1000)
        val CHECK_DATA_KEY_1001 = CheckDataKey(ACCOUNT_NUMBER, 1001)
        val ALL_CHECKS_LIST = listOf(newCheckData(1000), newCheckData(1001))
        val ALL_CHECK_KEYS = ALL_CHECKS_LIST.map { it.checkDataKey }.toSet()

        fun statementWithRecords(vararg records: TransactionHistoryRecord) = newBankStatement()
            .update(transactions = records.toList())

        fun historyRecordWithCheckData(checkNumber: Int?) = newHistoryRecord(checkNumber = checkNumber, checkData = newCheckData(checkNumber))
    }
}