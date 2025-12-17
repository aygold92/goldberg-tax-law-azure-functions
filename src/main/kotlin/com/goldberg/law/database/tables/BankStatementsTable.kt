package com.goldberg.law.database.tables

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
    val batesStamps = text("bates_stamps").nullable() // Store as JSON string
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
//    val manualVerification = enumeration()

    init {
        index(isUnique = false, classificationId)
        index(isUnique = false, accountNumber, date)
    }
}
