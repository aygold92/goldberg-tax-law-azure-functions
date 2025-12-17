package com.goldberg.law.database

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.BankStatementsTable
import com.goldberg.law.database.tables.ChecksTable
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.database.tables.TransactionsTable
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SchemaUtils
import org.jetbrains.exposed.sql.deleteAll
import org.jetbrains.exposed.sql.transactions.transaction
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.TestInstance
import java.time.Instant

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
 *   H2 doesn't ship MD5() even in MySQL mode, so setupSchema() registers it as a
 *   function alias backed by H2Functions.md5 (avoids H2's runtime Java compilation).
 * - MySQL-specific features not used by any tested service (full-text search, JSON
 *   functions) are out of scope per the spec.
 */
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
abstract class DatabaseTest {

    var beginningOfTest = Instant.now().toEpochMilli()

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
            // H2 doesn't include MD5() even in MySQL mode — register it via a pre-compiled
            // static method to avoid H2's runtime Java compilation (which fails due to
            // annotation processor conflicts on the test classpath).
            exec("""CREATE ALIAS IF NOT EXISTS MD5 FOR "com.goldberg.law.database.H2Functions.md5"""")

            // Drop first — Gradle daemon keeps the named H2 database alive across builds
            // (DB_CLOSE_DELAY=-1), so constraints from a prior run would conflict on CREATE.
            SchemaUtils.drop(
                TransactionsTable,
                ChecksTable,
                BankStatementsTable,
                ClassificationsTable,
                FilesTable,
                ClientsTable,
            )
            // Create in FK dependency order:
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
    fun beforeEach() {
        beginningOfTest = Instant.now().toEpochMilli()
    }

    @AfterAll
    fun clearData() {
        db.txnSafe {
            ClientsTable.deleteAll()
        }
    }

    fun assertTimeIsDuringTest(timeToCheck: Long, endOfTimeWindow: Long = Instant.now().toEpochMilli() + 1) {
        assertTimeInWindow(timeToCheck, beginningOfTest, endOfTimeWindow)
    }

    // exclusive
    fun assertTimeInWindow(timeToCheck: Long, beginningOfTimeWindow: Long, endOfTimeWindow: Long = Instant.now().toEpochMilli()) {
        assertThat(timeToCheck).isGreaterThan(beginningOfTimeWindow).isLessThan(endOfTimeWindow)
    }
}
