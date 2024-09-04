package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FIXED_TRANSACTION_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.CheckData
import com.goldberg.law.document.model.output.CheckDataKey
import com.goldberg.law.document.model.output.PageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CheckToStatementMatcherTest {
    val checkToStatementMatcher = CheckToStatementMatcher()
    @Test
    fun testAccountNumbersOnStatementAreBlankedOut() {
        val otherAccountNumber = "xxxxxxxxx7890"
        val bankStatement1 = statementWithRecords(BASIC_TH_RECORD).copy(accountNumber = otherAccountNumber)
        val bankStatement2 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1000)).copy(accountNumber = otherAccountNumber)
        val bankStatement3 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1001)).copy(accountNumber = otherAccountNumber)


        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement1, bankStatement2, bankStatement3), ALL_CHECKS_MAP
        )

        val bankStatement2CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1000)).copy(accountNumber = otherAccountNumber)
        val bankStatement3CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1001)).copy(accountNumber = otherAccountNumber)
        val finalStatementList = listOf(bankStatement1, bankStatement2CheckData, bankStatement3CheckData)
        assertThat(finalStatements).isEqualTo(finalStatementList)
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEmpty()
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdButNumbersMatch() {
        // this test should still pass because the number, date, etc. match
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement1 = statementWithRecords(BASIC_TH_RECORD).copy(accountNumber = otherAccountNumber)
        val bankStatement2 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1000)).copy(accountNumber = otherAccountNumber)
        val bankStatement3 = statementWithRecords(BASIC_TH_RECORD, newHistoryRecord(checkNumber = 1001)).copy(accountNumber = otherAccountNumber)


        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement1, bankStatement2, bankStatement3), ALL_CHECKS_MAP
        )

        val bankStatement2CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1000)).copy(accountNumber = otherAccountNumber)
        val bankStatement3CheckData = statementWithRecords(BASIC_TH_RECORD, historyRecordWithCheckData(1001)).copy(accountNumber = otherAccountNumber)
        val finalStatementList = listOf(bankStatement1, bankStatement2CheckData, bankStatement3CheckData)
        assertThat(finalStatements).isEqualTo(finalStatementList)
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEmpty()
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdDateDoesNotMatch() {
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement = newBankStatement(accountNumber = otherAccountNumber)
            .update(transactions = listOf(newHistoryRecord(date = fromWrittenDate("6 10 2020"), checkNumber = 1000)))


        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), mapOf(CHECK_DATA_KEY_1000 to newCheckData(1000))
        )

        assertThat(finalStatements).isEqualTo(listOf(bankStatement))
        assertThat(checksNotFound).isEqualTo(setOf(CheckDataKey(otherAccountNumber, 1000)))
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1000))
    }

    @Test
    fun testAccountNumbersOnStatementIsWeirdAmountDoesNotMatch() {
        val otherAccountNumber = "xxxxxxxxxxxxx"
        val bankStatement = newBankStatement(accountNumber = otherAccountNumber)
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000)))


        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), mapOf(CHECK_DATA_KEY_1000 to newCheckData(1000))
        )

        assertThat(finalStatements).isEqualTo(listOf(bankStatement))
        assertThat(checksNotFound).isEqualTo(setOf(CheckDataKey(otherAccountNumber, 1000)))
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1000))
    }

    @Test
    fun testAccountNumbersMatchButOtherStuffDoesNot() {
        // this should match because it's most likely that the record wasn't read the record correctly
        val bankStatement = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000)))


        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(bankStatement), mapOf(CHECK_DATA_KEY_1000 to newCheckData(1000))
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, checkNumber = 1000, checkData = newCheckData(1000))))

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEmpty()
    }

    // TODO: test cases where account mapping is weird

    @Test
    fun testAccountNumberMatchesMultiple() {
        val statementAccountNumber = "xxx7890"
        val otherAccountNumber = "567890"
        val checkData1 = newCheckData(1000, otherAccountNumber)
        val checkData2 = newCheckData(1001)

        val statement = statementWithRecords(newHistoryRecord(checkNumber = 1000)).copy(accountNumber = statementAccountNumber)

        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement), mapOf(CheckDataKey(otherAccountNumber, 1000) to checkData1, CHECK_DATA_KEY_1001 to checkData2)
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber)

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testAccountNumberMatchesNone() {
        // TODO: not sure if this should happen or not
        val statementAccountNumber = "xxx78902"
        val otherAccountNumber = "567890"
        val checkData1 = newCheckData(1000, otherAccountNumber)
        val checkData2 = newCheckData(1001)

        val statement = statementWithRecords(newHistoryRecord(checkNumber = 1000)).copy(accountNumber = statementAccountNumber)

        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement), mapOf(CheckDataKey(otherAccountNumber, 1000) to checkData1, CHECK_DATA_KEY_1001 to checkData2)
        )

        val bankStatementWithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber)

        assertThat(finalStatements).isEqualTo(listOf(bankStatementWithCheckData))
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1001))
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

        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement1, statement2), mapOf(CheckDataKey(otherAccountNumber, 1000) to checkData1, CHECK_DATA_KEY_1001 to checkData2)
        )

        val bankStatement1WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
        val bankStatement2WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(checkNumber = 1000, accountNumber = otherAccountNumber))))
            .copy(accountNumber = statementAccountNumber)

        assertThat(finalStatements).isEqualTo(listOf(bankStatement1WithCheckData, bankStatement2WithCheckData))
        assertThat(checksNotFound).isEmpty()
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    @Test
    fun testMultipleCheckNumbersDifferentAccountsDifferentData() {
        val statementAccountNumber = "xxx7890"
        val otherAccountNumber = "567890"
        val checkData1 = CheckData(
            otherAccountNumber,
            1000,
            "test",
            fromWrittenDate("4 10 2020"),
            1500.0,
            BASIC_PAGE_METADATA
        )
        val checkData2 = newCheckData(1001)

        val statement1 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000)
        )

        val statement2 = statementWithRecords(
            newHistoryRecord(checkNumber = 1000, amount = 1500.0, date = fromWrittenDate("4 10 2020"))
        ).copy(accountNumber = statementAccountNumber)

        val (finalStatements, checksNotFound, checksNotUsed) = checkToStatementMatcher.matchChecksWithStatements(
            listOf(statement1, statement2), mapOf(CheckDataKey(otherAccountNumber, 1000) to checkData1, CHECK_DATA_KEY_1001 to checkData2)
        )

        val bankStatement2WithCheckData = newBankStatement()
            .update(transactions = listOf(newHistoryRecord(amount = 1500.0, date = fromWrittenDate("4 10 2020"), checkNumber = 1000, checkData = checkData1)))
            .copy(accountNumber = statementAccountNumber)

        assertThat(finalStatements).isEqualTo(listOf(statement1, bankStatement2WithCheckData))
        assertThat(checksNotFound).isEqualTo(setOf(CHECK_DATA_KEY_1000))
        assertThat(checksNotUsed).isEqualTo(setOf(CHECK_DATA_KEY_1001))
    }

    companion object {
        val CHECK_DATA_KEY_1000 = CheckDataKey(ACCOUNT_NUMBER, 1000)
        val CHECK_DATA_KEY_1001 = CheckDataKey(ACCOUNT_NUMBER, 1001)
        val ALL_CHECKS_MAP = mapOf(
            CHECK_DATA_KEY_1000 to newCheckData(1000),
            CHECK_DATA_KEY_1001 to newCheckData(1001)
        )

        fun statementWithRecords(vararg records: TransactionHistoryRecord) = newBankStatement()
            .update(transactions = records.toList())

        fun historyRecordWithCheckData(checkNumber: Int?) = newHistoryRecord(checkNumber = checkNumber, checkData = newCheckData(checkNumber))
    }
}