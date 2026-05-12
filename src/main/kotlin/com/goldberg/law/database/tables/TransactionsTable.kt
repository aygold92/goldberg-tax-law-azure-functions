package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object TransactionsTable : UUIDTable("transactions", "transaction_id") {
    val statementId = reference("statement_id", BankStatementsTable.id, onDelete = ReferenceOption.CASCADE)
    val checkId = reference("check_id", ChecksTable.id, onDelete = ReferenceOption.SET_NULL).nullable()
    val date = varchar("date", 10).nullable()
    val checkNumber = integer("check_number").nullable()
    val description = text("description").nullable()
    val amount = decimal("amount", 15, 2).nullable()
    val filePageNumber = integer("file_page_number")
    val statementIndex = integer("statement_index")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    
    init {
        index(isUnique = false, statementId)
        index(isUnique = false, checkId)
        index(isUnique = false, statementId, checkNumber, checkId) // For finding unassigned transactions
    }
}
