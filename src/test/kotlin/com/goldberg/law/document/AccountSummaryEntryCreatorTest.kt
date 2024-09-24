package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.AccountSummaryEntry
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toMonthYear
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class AccountSummaryEntryCreatorTest {
    private val creator = AccountSummaryCreator()

    @Test
    fun creatorTestBasic() {
        val statements = listOf(
            newBankStatementNotSus("6 1, 2020"),
            newBankStatementNotSus("7 1, 2020"),
            newBankStatementNotSus("8 1, 2020"),
            newBankStatementNotSus("9 1, 2020"),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, emptyList(), emptyList())
        )
    }

    @Test
    fun creatorTestBasicMissing() {
        val statements = listOf(
            newBankStatementNotSus("6 1, 2020"),
            newBankStatementNotSus("7 1, 2020"),
            newBankStatementNotSus("9 1, 2020"),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, listOf("8/2020"), emptyList())
        )
    }

    @Test
    fun creatorTestBasicMissingAndSus() {
        val statements = listOf(
            newBankStatementNotSus("6 1, 2020"),
            newBankStatementNotSus("7 1, 2020"),
            newBankStatement(statementDate = "9 1, 2020"),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, listOf("8/2020"), listOf(fromWrittenDate("9 1, 2020")!!))
        )
    }

    @Test
    fun testCreateOneStatement() {
        val statements = listOf(
            newBankStatementNotSus("6 1, 2020")
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("6 1, 2020")!!, emptyList(), emptyList())
        )
    }

    @Test
    fun testMissingWithStatementDateOnDifferentDays() {
        val statements = listOf(
            newBankStatementNotSus("6 5, 2020"),
            newBankStatementNotSus("7 19, 2020"),
            newBankStatement(statementDate = "9 2, 2020"),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!, listOf("8/2020"), listOf(fromWrittenDate("9 2, 2020")!!))
        )
    }

    @Test
    fun testCreateManyMissing() {
        val statements = listOf(
            newBankStatementNotSus("6 1, 2020"),
            newBankStatementNotSus("8 1, 2022"),
        )
        val result = creator.createSummary(statements)

        val first = fromWrittenDate("6 1, 2020")!!
        val last = fromWrittenDate("8 1, 2022")!!
        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            AccountSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, first, last,
                generateMonthsBetween(first, last), emptyList())
        )
    }

    private fun generateMonthsBetween(startDate: Date, endDate: Date): List<String> {
        val calendar = Calendar.getInstance().apply {
            time = startDate
            set(Calendar.DAY_OF_MONTH, 1)
            add(Calendar.MONTH, 1)  // start one month ahead
        }

        val endTime = Calendar.getInstance().apply {
            time = endDate
            add(Calendar.MONTH, -1)  // end one month before
        }.time

        val allMonths = mutableListOf<String>()
        while (calendar.time.before(endTime) || calendar.time == endTime) {
            allMonths.add(calendar.time.toMonthYear())
            calendar.add(Calendar.MONTH, 1)
        }

        return allMonths
    }

    companion object {
        fun newBankStatementNotSus(
            statementDate: String? = FIXED_STATEMENT_DATE,
        ) = newBankStatement(ACCOUNT_NUMBER, statementDate)
            .update(beginningBalance = 500.asCurrency(), endingBalance = ZERO, transactions = listOf(newHistoryRecord(date = statementDate)))

    }
}