package com.goldberg.law.database.tables

import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object FilesTable : ClientTokenUUIDTable("files", "file_id") {
    val clientId = reference("client_id", ClientsTable.id, onDelete = ReferenceOption.CASCADE)
    val fileName = varchar("file_name", 500)
    val contentHash = uuid("content_hash")
    val uploadedAt = timestamp("uploaded_at").defaultExpression(CurrentTimestamp)
    val numPages = integer("num_pages")
    
    init {
        uniqueIndex(clientId, fileName)
        uniqueIndex(clientId, contentHash)
        uniqueIndex(clientId, clientToken)
        index(isUnique = false, clientId)
    }
    
}
