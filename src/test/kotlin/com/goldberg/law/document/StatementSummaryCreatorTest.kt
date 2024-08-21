package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.pdf.model.StatementType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toMonthYear
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.util.*

class StatementSummaryCreatorTest {
    private val creator = StatementSummaryCreator()

    @Test
    fun creatorTestBasic() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("7 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("8 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("9 1, 2020")),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, emptyList(), emptyList())
        )
    }

    @Test
    fun creatorTestBasicMissing() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("7 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("9 1, 2020")),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, listOf("8/2020"), emptyList())
        )
    }

    @Test
    fun creatorTestBasicMissingAndSus() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("7 1, 2020")),
            newBankStatement(statementDate = fromWrittenDate("9 1, 2020")),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("9 1, 2020")!!, listOf("8/2020"), listOf(fromWrittenDate("9 1, 2020")!!))
        )
    }

    @Test
    fun testCreateOneStatement() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 1, 2020"))
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 1, 2020")!!, fromWrittenDate("6 1, 2020")!!, emptyList(), emptyList())
        )
    }

    @Test
    fun testMissingWithStatementDateOnDifferentDays() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 5, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("7 19, 2020")),
            newBankStatement(statementDate = fromWrittenDate("9 2, 2020")),
        )
        val result = creator.createSummary(statements)

        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!, listOf("8/2020"), listOf(fromWrittenDate("9 2, 2020")!!))
        )
    }

    @Test
    fun testCreateManyMissing() {
        val statements = listOf(
            newBankStatementNotSus(statementDate = fromWrittenDate("6 1, 2020")),
            newBankStatementNotSus(statementDate = fromWrittenDate("8 1, 2022"))
        )
        val result = creator.createSummary(statements)

        val first = fromWrittenDate("6 1, 2020")!!
        val last = fromWrittenDate("8 1, 2022")!!
        assertThat(result).hasSize(1)
        assertThat(result[0]).isEqualTo(
            StatementSummaryEntry(ACCOUNT_NUMBER, BankTypes.WF_BANK, first, last,
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
            accountNumber: String? = ACCOUNT_NUMBER,
            statementDate: Date? = FIXED_STATEMENT_DATE
        ) = newBankStatement(accountNumber, statementDate)
            .update(beginningBalance = 500.0, endingBalance = 0.0, thPage = BASIC_TH_PAGE)

    }
}