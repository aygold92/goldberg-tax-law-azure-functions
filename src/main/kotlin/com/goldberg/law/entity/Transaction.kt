package com.goldberg.law.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.TransactionsTable
import com.goldberg.law.util.fromWrittenDate
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.time.Instant
import java.util.*

data class Transaction(
    val statementId: UUID,
    val transactionDetails: TransactionDetails,
    val checkDetails: CheckDetails? = null
): ITransaction by transactionDetails {
    @JsonIgnore
    fun getFinalDescription(): String? {
        val descriptionFromRecord = if (checkNumber != null && (!hasNumberWithoutCheck() || description == null))
            CHECK_FORMAT.format(transactionDetails.checkNumber)
        else if (checkNumber != null)
            "$description ${CHECK_FORMAT.format(transactionDetails.checkNumber)}"
        else
            description

        return if (descriptionFromRecord != null && checkDetails?.getFinalDescription() != null) {
            "$descriptionFromRecord: ${checkDetails.getFinalDescription()}"
        } else descriptionFromRecord ?: checkDetails?.getFinalDescription()
    }

    companion object {
        private const val CHECK_FORMAT = "Check %d"
        fun fromRow(row: ResultRow) = Transaction(
            statementId = row[BankStatementsTable.id].value,
            transactionDetails = TransactionDetails.fromRow(row),
            checkDetails = row[ChecksTable.id]?.let { CheckDetails.fromRow(row) }
        )
    }
}

data class TransactionDetails(
    override val transactionId: UUID,
    override val date: String?,
    override val description: String?,
    override val amount: BigDecimal?,
    override val checkNumber: Int?,
    override val filePageNumber: Int,
    override val statementIndex: Int = 0,
    override val checkId: UUID?,
    val createdAt: Long = Instant.now().toEpochMilli(),
    val updatedAt: Long = Instant.now().toEpochMilli(),
): ITransaction {
    override val transactionDate: Date? get() = fromWrittenDate(date)

    companion object {
        fun fromRow(row: ResultRow) = TransactionDetails(
            transactionId = row[TransactionsTable.id].value,
            date = row[TransactionsTable.date],
            description = row[TransactionsTable.description],
            amount = row[TransactionsTable.amount],
            checkNumber = row[TransactionsTable.checkNumber],
            filePageNumber = row[TransactionsTable.filePageNumber],
            statementIndex = row[TransactionsTable.statementIndex],
            checkId = row[TransactionsTable.checkId]?.value,
            createdAt = row[TransactionsTable.createdAt].toEpochMilli(),
            updatedAt = row[TransactionsTable.updatedAt].toEpochMilli(),
        )
    }
}

interface ITransaction {
    val transactionId: UUID
    val date: String?
    val description: String?
    val amount: BigDecimal?
    val checkNumber: Int?
    val filePageNumber: Int
    val statementIndex: Int
    val checkId: UUID?

    @get:JsonIgnore
    val transactionDate: Date?

    @JsonIgnore
    fun hasNumberWithoutCheck() = checkNumber != null && !hasCheckDescription() &&
            (this.description?.lowercase()?.contains(CHECK_DESCRIPTION, ignoreCase = false) != true)

    @JsonIgnore
    fun hasCheckDescription() = CHECK_DESCRIPTIONS.any { it.equals(this.description?.trim(), ignoreCase = true) }

    companion object {
        private const val CHECK_DESCRIPTION = "Check"
        private const val CHECK_DESCRIPTION_ALT = "Deposited OR Cashed Check"
        private val CHECK_DESCRIPTIONS = listOf(CHECK_DESCRIPTION.lowercase(), CHECK_DESCRIPTION_ALT.lowercase())
    }
}