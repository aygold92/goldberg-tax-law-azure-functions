package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object ChecksTable : UUIDTable("checks", "check_id") {
    val classificationId = reference("classification_id", ClassificationsTable.id)
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

class CheckEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<CheckEntity>(ChecksTable)
    
    var classificationId by ChecksTable.classificationId
    var checkNumber by ChecksTable.checkNumber
    var accountNumber by ChecksTable.accountNumber
    var to by ChecksTable.to
    var description by ChecksTable.description
    var date by ChecksTable.date
    var amount by ChecksTable.amount
    var batesStamp by ChecksTable.batesStamp
    var createdAt by ChecksTable.createdAt
    var updatedAt by ChecksTable.updatedAt
}
