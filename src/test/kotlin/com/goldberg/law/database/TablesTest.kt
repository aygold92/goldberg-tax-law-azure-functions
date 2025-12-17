package com.goldberg.law.database

import com.goldberg.law.database.tables.*
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test

class TablesTest {

    @Test
    fun printTables() {
        DbExec.txnSafe {
            val ddl = SchemaUtils.createStatements(ClientsTable, FilesTable, ClassificationsTable, BankStatementsTable, TransactionsTable, ChecksTable)
            ddl.forEach(::println)
        }
    }

    companion object {
        @BeforeAll
        @JvmStatic
        fun setup() {
            Database.connect("jdbc:sqlite::memory:", driver = "org.sqlite.JDBC")
        }
    }
}