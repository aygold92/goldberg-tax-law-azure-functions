package com.goldberg.law.database.tables

import com.fasterxml.jackson.core.type.TypeReference
import com.goldberg.law.util.OBJECT_MAPPER
import org.jetbrains.exposed.dao.UUIDEntity
import org.jetbrains.exposed.dao.UUIDEntityClass
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.dao.id.UUIDTable
import org.jetbrains.exposed.sql.javatime.CurrentTimestamp
import org.jetbrains.exposed.sql.javatime.timestamp
import java.util.*

object ClassificationsTable : UUIDTable("classifications", "classification_id") {
    val fileId = reference("file_id", FilesTable.id)
    val pages = text("pages") // Store as JSON string
    val classificationType = varchar("classification_type", 100)
    val modelLocation = text("model_location").nullable()

    val pagesHash = varchar("pages_hash", 32)
        .withDefinition("GENERATED ALWAYS AS (MD5(pages)) STORED")
        .databaseGenerated() // Computed column for uniqueness
    val createdAt = timestamp("created_at").defaultExpression(CurrentTimestamp)
    val updatedAt = timestamp("updated_at").defaultExpression(CurrentTimestamp)

    init {
        uniqueIndex(fileId, pagesHash)
        index(isUnique = false, fileId)
        index(isUnique = false, fileId, modelLocation)
    }
}

class ClassificationEntity(id: EntityID<UUID>) : UUIDEntity(id) {
    companion object : UUIDEntityClass<ClassificationEntity>(ClassificationsTable)

    var fileId by ClassificationsTable.fileId
    var pages: Set<Int> by ClassificationsTable.pages.transform({
        OBJECT_MAPPER.writeValueAsString(pagesSorted())
    }, { OBJECT_MAPPER.readValue(it, object: TypeReference<Set<Int>>(){}) })
    var classificationType by ClassificationsTable.classificationType
    var modelLocation by ClassificationsTable.modelLocation
    var pagesHash by ClassificationsTable.pagesHash
    var createdAt by ClassificationsTable.createdAt
    var updatedAt by ClassificationsTable.updatedAt

    fun pagesSorted() = pages.sorted()
}
