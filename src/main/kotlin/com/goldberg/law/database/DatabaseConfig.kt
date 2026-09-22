package com.goldberg.law.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import org.jetbrains.exposed.sql.Database
import java.sql.Connection

object DatabaseConfig {
    private val logger = KotlinLogging.logger {}
    
    private val dataSource: HikariDataSource by lazy {
        logger.info { "Initializing HikariCP connection pool" }
        
        val config = HikariConfig().apply {
            // Database connection
            jdbcUrl = buildJdbcUrl()
            username = System.getenv("MySql.user") ?: throw IllegalStateException("DB_USER environment variable not set")
            password = System.getenv("MySql.password") ?: throw IllegalStateException("DB_PASSWORD environment variable not set")
            driverClassName = "com.mysql.cj.jdbc.Driver"
            
            // Pool size for Azure Functions
            maximumPoolSize = 10
            minimumIdle = 0  // CRITICAL: Allow all connections to close when idle for auto-pause
            
            // Close idle connections after 60 seconds
            idleTimeout = 60000
            maxLifetime = 600000  // 10 minutes max connection lifetime
            
            // Handle database cold starts (60s wake-up + time for query)
            connectionTimeout = 120000  // 120 seconds total
            
            // Don't keep connections alive unnecessarily
            keepaliveTime = 0
            
            // Connection properties for Azure MySQL
            addDataSourceProperty("useSSL", "true")
            addDataSourceProperty("requireSSL", "true")
            addDataSourceProperty("serverTimezone", "UTC")
            
            // Connection validation
            connectionTestQuery = "SELECT 1"
            validationTimeout = 5000
        }
        
        HikariDataSource(config).also {
            logger.info { "HikariCP connection pool initialized with URL: ${config.jdbcUrl}" }
        }
    }

    private fun buildJdbcUrl(): String {
        val host = System.getenv("MySql.endpoint") ?: throw IllegalStateException("DB_HOST environment variable not set")
        val port = System.getenv("MySql.port") ?: "3306"
        val databaseName = System.getenv("MySql.dbname") ?: throw IllegalStateException("DB_NAME environment variable not set")

        // characterEncoding is pinned rather than left to the driver, which otherwise negotiates the wire
        // encoding from whatever the server reports. That negotiation lands on utf8mb4 against a utf8mb4
        // server, so this changes nothing today — it stops the outcome depending on the server's config.
        return "jdbc:mysql://$host:$port/$databaseName" +
            "?useSSL=true&requireSSL=true&serverTimezone=UTC&characterEncoding=UTF-8"
    }

    val database: Database by lazy {
        Flyway.configure()
            .dataSource(dataSource)
            .load()
            .migrate()
        verifyUtf8()
        Database.connect(dataSource)
    }

    /**
     * Refuses to start if anything between here and the column would mangle non-ASCII text.
     *
     * Storage and connection character sets are independent, and either one alone is enough to corrupt a
     * Greek statement or an accented payee: when the driver and server disagree about the encoding in transit,
     * the mojibake is then stored faithfully in a perfectly good utf8mb4 column, and nothing raises an error.
     * A failed cold start is a much cheaper outcome than silently eating a month of statements, so this throws.
     */
    private fun verifyUtf8() = dataSource.connection.use { connection ->
        // End to end rather than an inspection of character_set_* variables: encode in the driver, decode in
        // the server, come back. This fails on the condition that actually matters, including for reasons
        // neither the JDBC URL nor the server variables would have revealed.
        connection.prepareStatement("SELECT ? AS probe").use { statement ->
            statement.setString(1, UTF8_PROBE)
            statement.executeQuery().use { rows ->
                check(rows.next()) { "UTF-8 probe returned no rows" }
                val returned = rows.getString("probe")
                check(returned == UTF8_PROBE) {
                    "Database connection is not UTF-8 clean: sent \"$UTF8_PROBE\", got back \"$returned\". " +
                        "Check characterEncoding on the JDBC URL and the server's character_set_* variables."
                }
            }
        }

        // A table created under a non-utf8mb4 server default keeps that charset for good, even once the
        // database default is corrected, so the columns are the thing worth asserting on.
        connection.prepareStatement(NON_UTF8MB4_COLUMNS).use { statement ->
            statement.executeQuery().use { rows ->
                val offenders = mutableListOf<String>()
                while (rows.next()) {
                    offenders += "${rows.getString(1)}.${rows.getString(2)} is ${rows.getString(3)}"
                }
                check(offenders.isEmpty()) {
                    "These columns would mangle non-ASCII text: ${offenders.joinToString(", ")}. " +
                        "Expected utf8mb4 — see V5__utf8mb4.sql."
                }
            }
        }
        logger.info { "Verified utf8mb4 end to end" }
    }

    fun getConnection(): Connection = dataSource.connection

    fun init() {
        logger.info { "Initializing database connection pool" }
        database
    }
    
    fun close() {
        logger.info { "Closing HikariCP connection pool" }
        dataSource.close()
    }

    /** Greek for the statements that prompted this, plus an accented payee and a 4-byte character. */
    private const val UTF8_PROBE = "Ελλάδα Café Dupré \uD83D\uDCB0"

    /**
     * Flyway owns `flyway_schema_history` and V5 doesn't convert it, so failing on it would block startup
     * with no migration able to fix it — and it holds no client data to mangle either way.
     */
    private const val NON_UTF8MB4_COLUMNS = """
        SELECT TABLE_NAME, COLUMN_NAME, CHARACTER_SET_NAME
        FROM information_schema.COLUMNS
        WHERE TABLE_SCHEMA = DATABASE()
          AND TABLE_NAME <> 'flyway_schema_history'
          AND CHARACTER_SET_NAME IS NOT NULL
          AND CHARACTER_SET_NAME <> 'utf8mb4'
    """
}
