package com.goldberg.law.database.tables

import com.goldberg.law.entity.ReviewStatus
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object BankStatementsTable : UUIDTable("bank_statements", "statement_id") {
    val classificationId = reference("classification_id", ClassificationsTable.id, onDelete = ReferenceOption.CASCADE)
    val accountNumber = varchar("account_number", 50).nullable()
    val date = varchar("date", 10).nullable() // Store as string instead of date
    val beginningBalance = decimal("beginning_balance", 15, 2).nullable()
    val endingBalance = decimal("ending_balance", 15, 2).nullable()
    val interestCharged = decimal("interest_charged", 15, 2).nullable()
    val feesCharged = decimal("fees_charged", 15, 2).nullable()
    val accountName = varchar("account_name", 255).nullable()
    val startDate = varchar("start_date", 10).nullable()
    val totalCredits = decimal("total_credits", 15, 2).nullable()
    val totalDebits = decimal("total_debits", 15, 2).nullable()
    val checksTotal = decimal("checks_total", 15, 2).nullable()
    val interestReceived = decimal("interest_received", 15, 2).nullable()
    val txnCountCredit = integer("txn_count_credit").nullable()
    val txnCountDebit = integer("txn_count_debit").nullable()
    val txnCount = integer("txn_count").nullable()
    /** The summary fields printed as their own lines in the summary box's arithmetic, as a JSON list of property names. */
    val summaryArithmeticFields = text("summary_arithmetic_fields").nullable()
    /** Summary box lines that aren't a summary field, label -> amount, as JSON. */
    val otherCredits = text("other_credits").nullable()
    val otherDebits = text("other_debits").nullable()
    /** The extraction agent's `errors` for this account and its statement, as JSON. What the agent claimed; never edited. */
    val agentErrors = text("agent_errors").nullable()
    /** The summary fields the extraction agent flagged, as a JSON list of StatementDetails property names. */
    val reviewFields = text("review_fields").nullable()
    /** The extraction agent's notes for a reviewer, as a JSON list. */
    val reviewNotes = text("review_notes").nullable()
    val reviewStatus = enumerationByName("review_status", 16, ReviewStatus::class).nullable()
    // Deprecated: written by the Azure pipeline only. The agent path keeps the stamps on the classification.
    val batesStamps = text("bates_stamps").nullable() // Store as JSON string
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        index(isUnique = false, classificationId)
        index(isUnique = false, accountNumber, date)
    }
}
