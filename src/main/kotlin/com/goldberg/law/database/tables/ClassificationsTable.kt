package com.goldberg.law.database.tables

import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.ReferenceOption
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp

object ClassificationsTable : UUIDTable("classifications", "classification_id") {
    val fileId = reference("file_id", FilesTable.id, onDelete = ReferenceOption.CASCADE)
    val pages = text("pages") // Store as JSON string
    val classificationType = varchar("classification_type", 100)
    val modelLocation = text("model_location").nullable()

    val pagesHash = varchar("pages_hash", 64)
        .withDefinition("GENERATED ALWAYS AS (SHA2(pages, 256))")
        .databaseGenerated() // Computed column for uniqueness
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(fileId, pagesHash)
        index(isUnique = false, fileId)
        index(isUnique = false, fileId, modelLocation)
    }
}
