package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object FilesTable : ClientTokenUUIDTable("files", "file_id") {
    val clientId = reference("client_id", ClientsTable.id)
    val fileName = varchar("file_name", 500)
    val storageLocation = text("storage_location")
    val contentHash = uuid("content_hash")
    val uploadedAt = timestamp("uploaded_at").defaultExpression(CurrentTimestamp)
    val numPages = integer("num_pages")
    
    init {
        uniqueIndex(clientId, fileName)
        uniqueIndex(clientId, contentHash)
        index(isUnique = false, clientId)
    }
    
    // TODO: Update checkClientToken method to account for contentHash in duplicate detection
    // Currently checks clientId + fileName, should also consider contentHash
}

class FileEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<FileEntity>(FilesTable)
    
    var clientId by FilesTable.clientId
    var fileName by FilesTable.fileName
    var storageLocation by FilesTable.storageLocation
    var contentHash by FilesTable.contentHash
    var uploadedAt by FilesTable.uploadedAt
    var numPages by FilesTable.numPages
}
