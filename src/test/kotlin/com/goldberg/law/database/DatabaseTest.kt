package com.goldberg.law.database

import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.database.tables.TransactionsTable
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance

/**
 * Abstract base class for all database service integration tests.
 *
 * Each concrete subclass gets its own isolated H2 in-memory database, named after the
 * test class.  This means test classes never share state, even when run in parallel.
 *
 * Lifecycle (@TestInstance PER_CLASS):
 * - One test-class instance is created for the whole class run.
 * - @BeforeAll (non-static) creates the schema once against `db`.
 * - @BeforeEach clears all rows so each test starts with an empty database.
 *
 * Services under test accept a Database parameter.  Subclasses should construct them
 * with the `db` property exposed here so all operations target the isolated instance.
 *
 * H2 compatibility notes:
 * - ClassificationsTable has a GENERATED ALWAYS AS (MD5(pages)) STORED column.
 *   H2 in MySQL mode supports both MD5() and generated columns, so schema creation
 *   should succeed.  If it does not, the error will surface at schema creation time.
 * - MySQL-specific features not used by any tested service (full-text search, JSON
 *   functions) are out of scope per the spec.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DatabaseTest {

    /**
     * Isolated H2 in-memory database for this test class.
     * Pass this to every service constructor so all operations target this DB.
     */
    val db: Database = Database.connect(
        url = "jdbc:h2:mem:${this::class.simpleName?.lowercase()};MODE=MySQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
        driver = "org.h2.Driver"
    )

    @BeforeAll
    fun setupSchema() {
        transaction(db) {
            // Create tables in FK dependency order:
            // Clients → Files → Classifications → BankStatements + Checks → Transactions
            SchemaUtils.create(
                ClientsTable,
                FilesTable,
                ClassificationsTable,
                BankStatementsTable,
                ChecksTable,
                TransactionsTable,
            )
        }
    }

    @BeforeEach
    fun clearData() {
        transaction(db) {
            // Delete in reverse FK dependency order to avoid constraint violations
            TransactionsTable.deleteAll()
            BankStatementsTable.deleteAll()
            ChecksTable.deleteAll()
            ClassificationsTable.deleteAll()
            FilesTable.deleteAll()
            ClientsTable.deleteAll()
        }
    }
}
