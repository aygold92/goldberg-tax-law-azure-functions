package com.goldberg.law.entity

import com.goldberg.law.database.tables.ClientsTable
import net.minidev.json.annotate.JsonIgnore
import org.jetbrains.exposed.sql.ResultRow
import java.util.*

data class Client(
    override val clientId: UUID,
    override val clientName: String,
    val createdAt: Long,
): IClient {
    companion object {
        fun fromRow(row: ResultRow) = Client(
            clientId = row[ClientsTable.id].value,
            clientName = row[ClientsTable.name],
            createdAt = row[ClientsTable.createdAt].epochSecond
        )
    }
}

interface IClient {
    val clientId: UUID
    val clientName: String
}
