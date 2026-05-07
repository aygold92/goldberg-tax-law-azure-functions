package com.goldberg.law.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.type.TypeReference
import com.goldberg.law.database.tables.ClassificationsTable
import com.goldberg.law.datamanager.StorageLocation
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.OBJECT_MAPPER
import org.jetbrains.exposed.sql.ResultRow
import java.util.*

data class Classification(
    val inputFile: InputFile,
    val info: ClassificationInfo,
): IClassification by info, IInputFile by inputFile, IClient by inputFile.client {
    fun loggingInfo() = "[$classificationId] ${inputFile.fileName} - $pagesOrdered - $classificationType";
    fun isAnalyzed() = info.modelLocation != null

    companion object {
        fun fromRow(row: ResultRow) = Classification(
            inputFile = InputFile.fromRow(row),
            info = ClassificationInfo.fromRow(row)
        )
    }
}

data class ClassificationInfo(
    override val classificationId: UUID,
    override val pages: Set<Int>,
    override val classificationType: String,
    val modelLocation: StorageLocation? = null,
    val createdAt: Long,
    val updatedAt: Long,
): IClassification {
    override val documentType: DocumentType get() = DocumentType.getBankType(classificationType)

    override val pagesOrdered: List<Int> get() = pages.sorted()

    companion object {
        fun fromRow(row: ResultRow) = ClassificationInfo(
            classificationId = row[ClassificationsTable.id].value,
            pages = OBJECT_MAPPER.readValue(row[ClassificationsTable.pages], object : TypeReference<LinkedHashSet<Int>>() {}),
            classificationType = row[ClassificationsTable.classificationType],
            modelLocation = StorageLocation.deserialize(row[ClassificationsTable.modelLocation]),
            createdAt = row[ClassificationsTable.createdAt].toEpochMilli(),
            updatedAt = row[ClassificationsTable.updatedAt].toEpochMilli()
        )
    }
}

interface IClassification {
    val classificationId: UUID
    val pages: Set<Int>
    val classificationType: String

    @get:JsonIgnore
    val documentType: DocumentType
    @get:JsonIgnore
    val pagesOrdered: List<Int>

    fun toClassifiedPages() = ClassifiedPages(pages, classificationType)
}

