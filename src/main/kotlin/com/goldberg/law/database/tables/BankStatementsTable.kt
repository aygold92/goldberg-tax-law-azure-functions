package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object BankStatementsTable : UUIDTable("bank_statements", "statement_id") {
    val classificationId = reference("classification_id", ClassificationsTable.id)
    val accountNumber = char("account_number", 4).nullable()
    val date = varchar("date", 10).nullable() // Store as string instead of date
    val beginningBalance = decimal("beginning_balance", 15, 2).nullable()
    val endingBalance = decimal("ending_balance", 15, 2).nullable()
    val interestCharged = decimal("interest_charged", 15, 2).nullable()
    val feesCharged = decimal("fees_charged", 15, 2).nullable()
    val batesStamps = text("bates_stamps").nullable() // Store as JSON string
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        index(isUnique = false, classificationId)
        index(isUnique = false, accountNumber, date)
    }
}

class BankStatementEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<BankStatementEntity>(BankStatementsTable)
    
    var classificationId by BankStatementsTable.classificationId
    var accountNumber by BankStatementsTable.accountNumber
    var date by BankStatementsTable.date
    var beginningBalance by BankStatementsTable.beginningBalance
    var endingBalance by BankStatementsTable.endingBalance
    var interestCharged by BankStatementsTable.interestCharged
    var feesCharged by BankStatementsTable.feesCharged
    var batesStamps by BankStatementsTable.batesStamps
    var createdAt by BankStatementsTable.createdAt
    var updatedAt by BankStatementsTable.updatedAt
}
