package com.goldberg.law.agent.model.output

import com.fasterxml.jackson.databind.PropertyNamingStrategies
import com.fasterxml.jackson.databind.annotation.JsonNaming
import java.math.BigDecimal
import java.time.LocalDate

/**
 * Final output of `bank-statement-extraction`; see `skills/bank-statement-extraction/references/output-schema.md`.
 *
 * Fields the schema marks required are still nullable: it allows them to be legitimately missing, and reports
 * that in `errors` rather than omitting the statement.
 */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StatementExtractionOutput(
    val bankId: String?,
    val statementDate: LocalDate?,
    val statementStart: LocalDate? = null,
    val errors: List<String> = emptyList(),
    val reviewRequired: StatementReviewRequired? = null,
    val accounts: List<ExtractedAccount> = emptyList(),
)

/** Statement-level review: there are no transactions to index at this level. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class StatementReviewRequired(
    val fields: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

/** Every figure except a transaction's `amt` is an unsigned magnitude, exactly as printed. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExtractedAccount(
    val accountName: String? = null,
    val accountNumber: String?,
    val beginningBalance: BigDecimal?,
    val endingBalance: BigDecimal?,
    val totalCredits: BigDecimal? = null,
    val totalDebits: BigDecimal? = null,
    val txnCountCredit: Int? = null,
    val txnCountDebit: Int? = null,
    val txnCount: Int? = null,
    val checksTotal: BigDecimal? = null,
    val feesCharged: BigDecimal? = null,
    val interestReceived: BigDecimal? = null,
    val interestCharged: BigDecimal? = null,
    val dailyBalances: Map<LocalDate, BigDecimal> = emptyMap(),
    /** The summary fields printed as their own lines in the summary box's arithmetic. */
    val summaryArithmeticFields: List<String> = emptyList(),
    /** Box lines that aren't one of the summary fields, label -> printed amount: money in, then money out. */
    val otherCredits: Map<String, BigDecimal> = emptyMap(),
    val otherDebits: Map<String, BigDecimal> = emptyMap(),
    val errors: List<String> = emptyList(),
    val reviewRequired: AccountReviewRequired? = null,
    val transactions: List<ExtractedTransaction> = emptyList(),
)

/** `date`/`desc`/`check`/`amt` hold indexes into the account's `transactions`. */
@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class AccountReviewRequired(
    val date: List<Int> = emptyList(),
    val desc: List<Int> = emptyList(),
    val check: List<Int> = emptyList(),
    val amt: List<Int> = emptyList(),
    val fields: List<String> = emptyList(),
    val notes: List<String> = emptyList(),
)

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy::class)
data class ExtractedTransaction(
    val date: LocalDate?,
    val desc: String?,
    val check: String? = null,
    /** Signed by cash-flow direction from the holder's side: money in `+`, money out `-`. */
    val amt: BigDecimal?,
    val page: Int,
    /**
     * The printed figure, other than `total_credits`/`total_debits`, this line counts toward: `fees_charged`,
     * `interest_received`, `interest_charged`, or a key of `other_credits`/`other_debits`.
     */
    val countedIn: String? = null,
)
