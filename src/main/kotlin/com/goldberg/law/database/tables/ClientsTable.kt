package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object ClientsTable : ClientTokenUUIDTable("clients", "client_id") {
    val name = varchar("name", 64).uniqueIndex()
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)
}

class ClientEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ClientEntity>(ClientsTable)
    
    var name by ClientsTable.name
    var clientToken by ClientsTable.clientToken
    var createdAt by ClientsTable.createdAt
    var updatedAt by ClientsTable.updatedAt
}
