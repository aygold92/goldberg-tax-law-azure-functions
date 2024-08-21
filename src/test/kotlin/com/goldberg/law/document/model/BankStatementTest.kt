package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_BANK_STATEMENT
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.ModelValues.newTransactionPage
import com.goldberg.law.document.model.input.StatementDataModel.Keys.ENDING_BALANCE
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.TransactionHistoryPage
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.util.fromWrittenDate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BankStatementTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun testNetSpendingMultiplePages() {
        val statement = newBankStatement()
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)

        statement.update(thPage = newTransactionPage(1, 0.0))
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)
        statement.update(thPage = newTransactionPage(2, 500.0))
        assertThat(statement.getNetTransactions()).isEqualTo(500.0)
        statement.update(thPage = newTransactionPage(3, -500.0))
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)
        statement.update(thPage = newTransactionPage(4, -500.0))
        assertThat(statement.getNetTransactions()).isEqualTo(-500.0)
    }

    @Test
    fun testNetSpendingMultipleRecordsPerPage() {
        val statement = newBankStatement()
        statement.update(thPage = newTransactionPage(1, 10.0, 20.0, 40.0, 30.0, -50.0, -25.0))
        assertThat(statement.getNetTransactions()).isEqualTo(25.0)
        statement.update(thPage = newTransactionPage(2, 10.0, 20.0, 40.0, 30.0, -50.0, -25.0, -1000.0))
        assertThat(statement.getNetTransactions()).isEqualTo(-950.0)
    }

    @Test
    fun testToCsv() {
        assertThat(BASIC_BANK_STATEMENT.toCsv())
            .isEqualTo("4/3/2020,\"test\",-500.00,,$ACCOUNT_NUMBER,\"AG-12345\",4/7/2020,2,\"$FILENAME\",1")
            .isEqualTo(BASIC_TH_PAGE.toCsv(ACCOUNT_NUMBER))
    }

    @Test
    fun testToCsvMultiplePages() {
        val page = BASIC_TH_PAGE.copy(
            statementPageNum = 3,
            transactionHistoryRecords = listOf(BASIC_TH_RECORD, BASIC_TH_RECORD.copy(amount = 1500.00))
        )
        val statement = BASIC_BANK_STATEMENT.copy().update(thPage = page)
        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,${ACCOUNT_NUMBER},"AG-12345",4/7/2020,2,"$FILENAME",1
                4/3/2020,"test",-500.00,,${ACCOUNT_NUMBER},"AG-12345",4/7/2020,3,"$FILENAME",1
                4/3/2020,"test",1500.00,,${ACCOUNT_NUMBER},"AG-12345",4/7/2020,3,"$FILENAME",1
            """.trimIndent()
        )
    }

    @Test
    fun testToCsvMultiplePagesSorted() {
        val pageBefore = BASIC_TH_PAGE.copy(
            statementPageNum = 1,
            transactionHistoryRecords = listOf(
                BASIC_TH_RECORD,
                BASIC_TH_RECORD.copy(amount = 0.0),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("3/8", "2019")),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("8/3", "2024"))
            )
        )
        val pageAfter = BASIC_TH_PAGE.copy(
            statementPageNum = 3,
            transactionHistoryRecords = listOf(
                BASIC_TH_RECORD,
                BASIC_TH_RECORD.copy(amount = null),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("3/8", "2019")),
                BASIC_TH_RECORD.copy(date = fromWrittenDate("8/3", "2024"))
            )
        )
        val statement = newBankStatement().update(
            totalPages = 5,
            beginningBalance = 500.00,
            endingBalance = 1000.00,
        ).update(thPage = BASIC_TH_PAGE)
            .update(thPage = pageAfter)
            .update(thPage = pageBefore)
        assertThat(statement.toCsv()).isEqualTo(
            """
                3/8/2019,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,1,"$FILENAME",1
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,1,"$FILENAME",1
                4/3/2020,"test",0.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,1,"$FILENAME",1
                8/3/2024,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,1,"$FILENAME",1
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                3/8/2019,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",1
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",1
                4/3/2020,"test",,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",1
                8/3/2024,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",1
            """.trimIndent()
        )
    }

    @Test
    fun testSuspiciousStatementSingleRecordIncorrect() {
        val statement = newBankStatement().update(
            accountNumber = ACCOUNT_NUMBER,
            totalPages = 5,
            beginningBalance = 2000.00,
            endingBalance = 1000.00,
            thPage = BASIC_TH_PAGE
        ).update(thPage = newTransactionPage(3, BASIC_TH_RECORD, BASIC_TH_RECORD.copy(amount = null)))

        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,2,"$FILENAME",1
                4/3/2020,"test",-500.00,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",3
                4/3/2020,"test",,,$ACCOUNT_NUMBER,"AG-12345",4/7/2020,3,"$FILENAME",3
            """.trimIndent()
        )
        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(3)
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_PAGES }
        assertThat(suspiciousReasons).anyMatch { it == TransactionHistoryPage.SuspiciousReasons.HAS_SUSPICIOUS_RECORDS.format(3) }
        assertThat(suspiciousReasons).anyMatch { it == TransactionHistoryRecord.SuspiciousReasons.ONE_OF_WITHDRAWAL_DEPOSIT.format("4/3/2020") }
    }

    @Test
    fun testSuspiciousStatementOnlyNumbersDoNotAddUp() {
        val statement = newBankStatement().update(
            accountNumber = ACCOUNT_NUMBER,
            beginningBalance = 0.0,
            endingBalance = 50.0,
        ).update(thPage = newTransactionPage(1, 10.0, 20.0, 40.0))

        assertThat(statement.getNetTransactions()).isEqualTo(70.0)
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(1)
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(0.0, 70.0, 50.0 )}
    }

    @Test
    fun testSuspiciousReasonsMultiple() {
        val statement = newBankStatement().update(
            beginningBalance = 0.0,
            endingBalance = 50.0,
            accountNumber = ACCOUNT_NUMBER
        )
            .update(beginningBalance = 0.0, endingBalance = 100.0)
            .update(thPage = newTransactionPage(1, 10.0, 20.0, 40.0, 30.0, -50.0, -25.0))
            .update(thPage = newTransactionPage(2, newHistoryRecord(0.0)))
        assertThat(statement.getNetTransactions()).isEqualTo(25.0)
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        assertThat(statement.hasSuspiciousPages()).isTrue()

        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(5)
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_PAGES }
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(0.0, 25.0, 100.0 )}
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(ENDING_BALANCE, 50.0.toString(), 100.0.toString() )}
        assertThat(suspiciousReasons).anyMatch { it == TransactionHistoryRecord.SuspiciousReasons.ONE_OF_WITHDRAWAL_DEPOSIT.format("4/3/2020")}
    }
}