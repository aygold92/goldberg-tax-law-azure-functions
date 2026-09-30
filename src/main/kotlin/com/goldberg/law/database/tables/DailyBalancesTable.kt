package com.goldberg.law.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.Table

/** A statement's printed daily balance table: one row per day, which can run to hundreds on a multi-month statement. */
object DailyBalancesTable : Table("daily_balances") {
    val statementId = reference("statement_id", BankStatementsTable.id, onDelete = ReferenceOption.CASCADE)
    val date = varchar("date", 10)
    val balance = decimal("balance", 15, 2)

    override val primaryKey = PrimaryKey(statementId, date)
}
