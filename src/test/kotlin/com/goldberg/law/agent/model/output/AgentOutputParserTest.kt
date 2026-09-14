package com.goldberg.law.agent.model.output

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate

class AgentOutputParserTest {

    private fun fixture(name: String): String =
        javaClass.classLoader.getResource("agent/$name")!!.readText()

    private fun <T> parseOutput(text: String, type: Class<T>): T {
        val result = AgentOutputParser.parse(text, type)
        assertThat(result).isInstanceOf(AgentResult.Output::class.java)
        @Suppress("UNCHECKED_CAST")
        return (result as AgentResult.Output<T>).value
    }

    @Test
    fun `parses the splitter schema example`() {
        val output = parseOutput(fixture("splitter-output.json"), SplitterOutput::class.java)

        assertThat(output.banks).containsOnlyKeys("bank_of_america", "chase_cc")
        assertThat(output.banks.getValue("chase_cc")).isEqualTo(SplitterBank("Chase", "discovered"))
        assertThat(output.boundaries).containsExactly(
            StatementBoundary(1, 7, "bank_of_america"),
            StatementBoundary(8, 12, "chase_cc"),
        )
        assertThat(output.checkPages).containsExactly(5, 6)
        assertThat(output.bates!!.sequences).hasSize(2)
        assertThat(output.bates!!.sequences[0]).isEqualTo(BatesSequence(1, 412, "MH-000001", "MH-000412"))
        assertThat(output.bates!!.nonSequenced).containsExactlyEntriesOf(mapOf(413 to "MH-000999"))
        // omitted keys come back empty, not null
        assertThat(output.unassignedPages).isEmpty()
        assertThat(output.reviewRequired).isEmpty()
    }

    @Test
    fun `parses splitter review items, with and without pages`() {
        val output = parseOutput(fixture("splitter-output-review.json"), SplitterOutput::class.java)

        assertThat(output.unassignedPages).containsExactly(10)
        assertThat(output.bates).isNull()
        assertThat(output.reviewRequired).hasSize(2)
        assertThat(output.reviewRequired[0].pages).containsExactly(47, 48)
        assertThat(output.reviewRequired[1].pages).isEmpty()
        assertThat(output.reviewRequired[1].reason).startsWith("pages 82-90")
    }

    @Test
    fun `parses the statement extraction schema example`() {
        val output = parseOutput(fixture("statement-extraction-output.json"), StatementExtractionOutput::class.java)

        assertThat(output.bankId).isEqualTo("bank_of_america_combined")
        assertThat(output.statementDate).isEqualTo(LocalDate.of(2024, 1, 31))
        assertThat(output.statementStart).isEqualTo(LocalDate.of(2024, 1, 1))
        assertThat(output.errors).isEmpty()
        assertThat(output.reviewRequired).isNull()

        val account = output.accounts.single()
        assertThat(account.accountNumber).isEqualTo("4460 5473 8649")
        assertThat(account.beginningBalance).isEqualTo(BigDecimal("215871.68"))
        assertThat(account.totalCredits).isEqualTo(BigDecimal("250678.39"))
        assertThat(account.txnCountDebit).isEqualTo(47)
        assertThat(account.txnCount).isNull()
        assertThat(account.checksTotal).isEqualTo(BigDecimal("0.00"))
        assertThat(account.interestReceived).isNull()
        assertThat(account.dailyBalances).containsExactlyEntriesOf(
            mapOf(LocalDate.of(2024, 1, 4) to BigDecimal("365871.68"), LocalDate.of(2024, 1, 5) to BigDecimal("365821.68"))
        )
        assertThat(account.errors).isEmpty()
        assertThat(account.reviewRequired).isNull()

        val txn = account.transactions.single()
        assertThat(txn.date).isEqualTo(LocalDate.of(2024, 1, 4))
        assertThat(txn.check).isNull()
        // exact, including scale — money must not pass through a lossy representation
        assertThat(txn.amt).isEqualTo(BigDecimal("150000.00"))
        assertThat(txn.page).isEqualTo(14)
    }

    @Test
    fun `parses statement and account level issue containers`() {
        val output = parseOutput(fixture("statement-extraction-output-errors.json"), StatementExtractionOutput::class.java)

        assertThat(output.statementDate).isNull()
        assertThat(output.errors).containsExactly("summary_missing_fields")
        assertThat(output.reviewRequired!!.fields).containsExactly("statement_date")
        assertThat(output.reviewRequired!!.notes).isEmpty()

        val account = output.accounts.single()
        assertThat(account.beginningBalance).isNull()
        assertThat(account.errors).containsExactly("summary_missing_fields", "daily_ledger_mismatch")
        assertThat(account.reviewRequired!!.amt).containsExactly(12, 13, 14, 15)
        assertThat(account.reviewRequired!!.date).containsExactly(12, 13, 14, 15)
        assertThat(account.reviewRequired!!.desc).isEmpty()
        assertThat(account.reviewRequired!!.fields).containsExactly("beginning_balance")
        assertThat(account.reviewRequired!!.notes).containsExactly("rows 12-15 appear misaligned")
        assertThat(account.transactions.single().check).isEqualTo("1042")
        assertThat(account.transactions.single().amt).isEqualTo(BigDecimal("-1250.00"))
    }

    @Test
    fun `parses the check extraction schema example`() {
        val output = parseOutput(fixture("check-extraction-output.json"), CheckExtractionOutput::class.java)

        assertThat(output.checks).containsExactly(
            ExtractedCheck(5, 1042, "8558", LocalDate.of(2024, 1, 17), BigDecimal("1250.00"), "Unalome House LLC", "January rent", false),
            ExtractedCheck(5, null, "8558", LocalDate.of(2024, 1, 19), BigDecimal("340.00"), "Chesapeake Lawn & Landscape", null, true),
        )
        assertThat(output.pagesWithNoChecks).containsExactly(7)
        assertThat(output.unreadablePages).isEmpty()
    }

    @Test
    fun `an explicit null for a list reads as empty`() {
        val output = parseOutput("""{"checks": null, "pages_with_no_checks": null}""", CheckExtractionOutput::class.java)

        assertThat(output.checks).isEmpty()
        assertThat(output.pagesWithNoChecks).isEmpty()
    }

    @Test
    fun `recognizes the too-large-to-return-inline shape`() {
        assertThat(AgentOutputParser.parse("""{"file": "statement.json"}""", StatementExtractionOutput::class.java))
            .isEqualTo(AgentResult.OutputFile("statement.json"))
    }

    @Test
    fun `recognizes the nothing-to-extract shape`() {
        assertThat(AgentOutputParser.parse("""{"error": "pages 3-9 are a cover letter"}""", CheckExtractionOutput::class.java))
            .isEqualTo(AgentResult.Error("pages 3-9 are a cover letter"))
    }

    @Test
    fun `an error key alongside other keys is output, not the error shape`() {
        val text = """{"checks": [], "error": "unexpected"}"""

        assertThat(AgentOutputParser.parse(text, CheckExtractionOutput::class.java))
            .isEqualTo(AgentResult.Output(CheckExtractionOutput()))
    }

    @Test
    fun `rejects text that is not a JSON object`() {
        assertThatThrownBy { AgentOutputParser.parse("""[1, 2]""", CheckExtractionOutput::class.java) }
            .isInstanceOf(IllegalArgumentException::class.java)
        assertThatThrownBy { AgentOutputParser.parse("Here is the JSON you asked for", CheckExtractionOutput::class.java) }
            .isInstanceOf(Exception::class.java)
    }

    @Test
    fun `serializes back to the schema's names with ISO dates`() {
        val output = parseOutput(fixture("statement-extraction-output.json"), StatementExtractionOutput::class.java)

        val json = AgentOutputParser.JSON.writeValueAsString(output)

        assertThat(json)
            .contains(""""statement_date":"2024-01-31"""")
            .contains(""""daily_balances":{"2024-01-04":365871.68""")
            .contains(""""amt":150000.00""")
    }
}
