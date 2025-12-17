package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.ClientEntity
import com.goldberg.law.database.tables.ClientsTable
import com.goldberg.law.database.tables.withValue
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.entity.Client
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.selectAll
import java.util.*

class ClientService @Inject constructor() {
    private val logger = KotlinLogging.logger {}

    /**
     * Save client with idempotency token, return client_id
     * If client exists with same token, returns existing client ID
     * If client exists with different token, throws exception
     */
    fun insertClient(name: String, clientToken: UUID): UUID = DbExec.txnSafe {
        try {
            val newClient = ClientEntity.new {
                this.name = name
                this.clientToken = clientToken
            }
            logger.info { "Created new client: $name with ID: $newClient.id" }
            newClient.id.value
        } catch (ex: Exception) {
            ClientsTable.checkClientToken(ex, clientToken, ClientsTable.name withValue name)
        }
    }

    fun loadClient(clientId: UUID) = DbExec.txnSafe {
        ClientsTable.select(ClientsTable.id eq clientId)
            .map { Client.fromRow(it) }
            .singleOrNull() ?: throw EntityNotFoundException("Could not find client $clientId")
    }

    fun listClients() = DbExec.txnSafe {
        ClientsTable.selectAll()
            .map { Client.fromRow(it) }
    }
}