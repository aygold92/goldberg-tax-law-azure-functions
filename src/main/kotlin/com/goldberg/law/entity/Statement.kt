package com.goldberg.law.entity

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.module.kotlin.readValue
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.util.OBJECT_MAPPER
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class Statement(
    val classification: Classification,
    val statementDetails: StatementDetails,
    val suspiciousReasons: List<String>,
    val transactions: List<TransactionDetails>,
    /** Date (`YYYY-MM-DD`) -> balance, in date order. Loaded only with the full statement, never in summaries. */
    val dailyBalances: Map<String, BigDecimal> = emptyMap(),
): IStatementDetails by statementDetails, IClassification by classification

data class StatementDetails(
    override val statementId: UUID,
    override val date: String?,
    override val accountNumber: String?,
    override val beginningBalance: BigDecimal? = null,
    override val endingBalance: BigDecimal? = null,
    override val interestCharged: BigDecimal? = null,
    override val feesCharged: BigDecimal? = null,
    /** Deprecated: the Azure pipeline's copy. Agent-created statements read [ClassificationInfo.batesStamps]. */
    override val batesStamps: Map<Int, String>,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
    override val accountName: String? = null,
    /** The statement period's first day; [date] is its last. */
    override val startDate: String? = null,
    override val totalCredits: BigDecimal? = null,
    override val totalDebits: BigDecimal? = null,
    override val checksTotal: BigDecimal? = null,
    override val interestReceived: BigDecimal? = null,
    override val txnCountCredit: Int? = null,
    override val txnCountDebit: Int? = null,
    override val txnCount: Int? = null,
    /** The summary fields printed as their own lines in the summary box's arithmetic, as property names of this class. */
    override val summaryArithmeticFields: List<String> = emptyList(),
    /** Summary box lines that aren't one of this class's fields, label -> amount: money in. */
    override val otherCredits: Map<String, BigDecimal> = emptyMap(),
    /** Summary box lines that aren't one of this class's fields, label -> amount: money out. */
    override val otherDebits: Map<String, BigDecimal> = emptyMap(),
    /**
     * The extraction agent's `errors` for this account and for the statement it's on, verbatim. Resolved by
     * fixing the data, so never edited.
     */
    val agentErrors: List<String> = emptyList(),
    /** The summary fields the extraction agent flagged, as property names of this class (e.g. `beginningBalance`). */
    val reviewFields: List<String> = emptyList(),
    /** The extraction agent's notes for a reviewer, about this account or the statement it's on. */
    val reviewNotes: List<String> = emptyList(),
    /** Covers [reviewFields] and [reviewNotes] together; null when the agent flagged neither. */
    val reviewStatus: ReviewStatus? = null,
): IStatementDetails {
    override fun statementDate(): Date? = fromWrittenDate(date)

    companion object {
        fun fromRow(row: ResultRow) = StatementDetails(
            statementId = row[BankStatementsTable.id].value,
            date = row[BankStatementsTable.date],
            accountNumber = row[BankStatementsTable.accountNumber],
            beginningBalance = row[BankStatementsTable.beginningBalance]?.asCurrency(),
            endingBalance = row[BankStatementsTable.endingBalance]?.asCurrency(),
            interestCharged = row[BankStatementsTable.interestCharged]?.asCurrency(),
            feesCharged = row[BankStatementsTable.feesCharged]?.asCurrency(),
            batesStamps = OBJECT_MAPPER.readValue(row[BankStatementsTable.batesStamps], object : TypeReference<Map<Int, String>>() {}),
            createdAt = row[BankStatementsTable.createdAt].toEpochMilli(),
            updatedAt = row[BankStatementsTable.updatedAt].toEpochMilli(),
            accountName = row[BankStatementsTable.accountName],
            startDate = row[BankStatementsTable.startDate],
            totalCredits = row[BankStatementsTable.totalCredits]?.asCurrency(),
            totalDebits = row[BankStatementsTable.totalDebits]?.asCurrency(),
            checksTotal = row[BankStatementsTable.checksTotal]?.asCurrency(),
            interestReceived = row[BankStatementsTable.interestReceived]?.asCurrency(),
            txnCountCredit = row[BankStatementsTable.txnCountCredit],
            txnCountDebit = row[BankStatementsTable.txnCountDebit],
            txnCount = row[BankStatementsTable.txnCount],
            summaryArithmeticFields = row[BankStatementsTable.summaryArithmeticFields]?.let { OBJECT_MAPPER.readValue<List<String>>(it) }.orEmpty(),
            otherCredits = row[BankStatementsTable.otherCredits]?.let { OBJECT_MAPPER.readValue<Map<String, BigDecimal>>(it) }.orEmpty(),
            otherDebits = row[BankStatementsTable.otherDebits]?.let { OBJECT_MAPPER.readValue<Map<String, BigDecimal>>(it) }.orEmpty(),
            agentErrors = row[BankStatementsTable.agentErrors]?.let { OBJECT_MAPPER.readValue<List<String>>(it) }.orEmpty(),
            reviewFields = row[BankStatementsTable.reviewFields]?.let { OBJECT_MAPPER.readValue<List<String>>(it) }.orEmpty(),
            reviewNotes = row[BankStatementsTable.reviewNotes]?.let { OBJECT_MAPPER.readValue<List<String>>(it) }.orEmpty(),
            reviewStatus = row[BankStatementsTable.reviewStatus],
        )
    }
}

interface IStatementDetails {
    val statementId: UUID
    val date: String?
    val accountNumber: String?
    val beginningBalance: BigDecimal?
    val endingBalance: BigDecimal?
    val interestCharged: BigDecimal?
    val feesCharged: BigDecimal?
    val batesStamps: Map<Int, String>
    val accountName: String?
    val startDate: String?
    val totalCredits: BigDecimal?
    val totalDebits: BigDecimal?
    val checksTotal: BigDecimal?
    val interestReceived: BigDecimal?
    val txnCountCredit: Int?
    val txnCountDebit: Int?
    val txnCount: Int?
    val summaryArithmeticFields: List<String>
    val otherCredits: Map<String, BigDecimal>
    val otherDebits: Map<String, BigDecimal>
    fun statementDate(): Date?
}