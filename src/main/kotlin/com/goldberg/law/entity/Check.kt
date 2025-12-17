package com.goldberg.law.entity

import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.TransactionsTable
import org.jetbrains.exposed.sql.ResultRow
import java.math.BigDecimal
import java.util.UUID

data class Check(
    val classification: Classification,
    val checkDetails: CheckDetails,
): IClassification by classification, IInputFile by classification.inputFile, ICheck by checkDetails

data class CheckDetails(
    override val checkId: UUID,
    override val checkNumber: Int?,
    override val accountNumber: String?,
    override val description: String?,
    override val date: String?,
    override val amount: BigDecimal?,
    override val to: String?,
    override val batesStamp: String?,
): ICheck {
    fun getFinalDescription() = if (this.to != null && this.description != null) "${this.to} - ${this.description}"
    else this.to ?: this.description

    companion object {
        fun fromRow(row: ResultRow): CheckDetails = CheckDetails(
            checkId = row[ChecksTable.id].value,
            checkNumber = row[ChecksTable.checkNumber],
            accountNumber = row[ChecksTable.accountNumber],
            description = row[ChecksTable.description],
            date = row[ChecksTable.date],
            amount = row[ChecksTable.amount],
            to = row[ChecksTable.to],
            batesStamp = row[ChecksTable.batesStamp]
        )
    }
}

interface ICheck {
    val checkId: UUID
    val checkNumber: Int?
    val accountNumber: String?
    val description: String?
    val date: String?
    val amount: BigDecimal?
    val to: String?
    val batesStamp: String?
}