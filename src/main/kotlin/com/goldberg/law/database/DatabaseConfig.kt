package com.goldberg.law.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import java.sql.Connection

object DatabaseConfig {
    private val logger = KotlinLogging.logger {}
    
    private val dataSource: HikariDataSource by lazy {
        logger.info { "Initializing HikariCP connection pool" }
        
        val config = HikariConfig().apply {
            // Database connection
            jdbcUrl = buildJdbcUrl()
            username = System.getenv("DB_USER") ?: throw IllegalStateException("DB_USER environment variable not set")
            password = System.getenv("DB_PASSWORD") ?: throw IllegalStateException("DB_PASSWORD environment variable not set")
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
        val host = System.getenv("DB_HOST") ?: throw IllegalStateException("DB_HOST environment variable not set")
        val port = System.getenv("DB_PORT") ?: "3306"
        val databaseName = System.getenv("DB_NAME") ?: throw IllegalStateException("DB_NAME environment variable not set")

        return "jdbc:mysql://$host:$port/$databaseName?useSSL=true&requireSSL=true&serverTimezone=UTC"
    }

    val database: Database by lazy {
        Database.connect(dataSource)
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
}




