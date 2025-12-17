package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.withValue
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Client
import com.goldberg.law.entity.EntityType
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.Database
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.deleteWhere
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class ClientService @Inject constructor(
    private val db: Database,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * Save client with idempotency token, return client_id
     * If client exists with same token, returns existing client ID
     * If client exists with different token, throws exception
     */
    fun insertClient(name: String, clientToken: UUID): UUID = db.txnSafe {
        try {
            val newClient = ClientsTable.insert {
                it[ClientsTable.name] = name
                it[ClientsTable.clientToken] = clientToken
            }
            logger.info { "Created new client: $name with ID: ${newClient[ClientsTable.id].value}" }
            newClient[ClientsTable.id].value
        } catch (ex: Exception) {
            ClientsTable.checkClientToken(ex, clientToken, listOf(ClientsTable.name withValue name) to "client \"$name\" already exists")
        }
    }

    fun loadClient(clientId: UUID) = db.txnSafe {
        ClientsTable.selectAll().where { ClientsTable.id eq clientId }
            .map { Client.fromRow(it) }
            .singleOrNull() ?: throw EntityNotFoundException(EntityType.Client, clientId)
    }

    fun listClients() = db.txnSafe {
        ClientsTable.selectAll()
            .map { Client.fromRow(it) }
    }

    fun deleteClient(clientId: UUID) = db.txnSafe {
        ClientsTable.deleteWhere { ClientsTable.id eq clientId }
            .takeUnless { it == 0 } ?: throw EntityNotFoundException(EntityType.Client, clientId)
    }

}