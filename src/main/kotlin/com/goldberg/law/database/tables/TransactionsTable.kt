package com.goldberg.law.database.tables

import com.goldberg.law.entity.ReviewStatus
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
    /** The statement figure, besides total credits/debits, this line counts toward: a property name or an other-line label. */
    val countedIn = varchar("counted_in", 64).nullable()
    /** The fields the extraction agent flagged on this row, as a JSON list of TransactionDetails property names. */
    val reviewFields = text("review_fields").nullable()
    val reviewStatus = enumerationByName("review_status", 16, ReviewStatus::class).nullable()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
    
    init {
        index(isUnique = false, statementId)
        index(isUnique = false, checkId)
        index(isUnique = false, statementId, checkNumber, checkId) // For finding unassigned transactions
    }
}
