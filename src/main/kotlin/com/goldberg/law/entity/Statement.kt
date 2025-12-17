package com.goldberg.law.entity

import com.fasterxml.jackson.core.type.TypeReference
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
): IStatementDetails by statementDetails, IClassification by classification

data class StatementDetails(
    override val statementId: UUID,
    override val date: String?,
    override val accountNumber: String?,
    override val beginningBalance: BigDecimal? = null,
    override val endingBalance: BigDecimal? = null,
    override val interestCharged: BigDecimal? = null,
    override val feesCharged: BigDecimal? = null,
    override val batesStamps: Map<Int, String>,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
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
    fun statementDate(): Date?
}