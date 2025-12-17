package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ChecksTable : UUIDTable("checks", "check_id") {
    val classificationId = reference("classification_id", ClassificationsTable.id, onDelete = ReferenceOption.CASCADE)
    val checkNumber = integer("check_number").nullable()
    val accountNumber = varchar("account_number", 50).nullable()
    val to = varchar("to", 255).nullable()
    val description = text("description").nullable()
    val date = varchar("date", 50).nullable() // Store as string instead of date
    val amount = decimal("amount", 15, 2).nullable()
    val batesStamp = varchar("bates_stamp", 100).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    
    init {
        index(isUnique = false, classificationId)
        index(isUnique = false, accountNumber, checkNumber)
    }
}
