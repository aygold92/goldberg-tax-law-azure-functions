package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object TransactionsTable : UUIDTable("transactions", "transaction_id") {
    val statementId = reference("statement_id", BankStatementsTable.id)
    val checkId = reference("check_id", ChecksTable.id).nullable()
    val date = varchar("date", 10).nullable()
    val checkNumber = integer("check_number").nullable()
    val description = text("description").nullable()
    val amount = decimal("amount", 15, 2).nullable()
    val filePageNumber = integer("file_page_number")
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    
    init {
        index(isUnique = false, statementId)
        index(isUnique = false, checkId)
        index(isUnique = false, statementId, checkNumber, checkId) // For finding unassigned transactions
    }
}

class TransactionEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<TransactionEntity>(TransactionsTable)
    
    var statementId by TransactionsTable.statementId
    var checkId by TransactionsTable.checkId
    var transactionDate by TransactionsTable.date
    var checkNumber by TransactionsTable.checkNumber
    var description by TransactionsTable.description
    var amount by TransactionsTable.amount
    var filePageNumber by TransactionsTable.filePageNumber
    var createdAt by TransactionsTable.createdAt
    var updatedAt by TransactionsTable.updatedAt
}
