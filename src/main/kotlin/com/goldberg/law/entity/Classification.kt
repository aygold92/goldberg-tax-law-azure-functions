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
    fun toClassifiedPages() = info.toClassifiedPages()
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
    @JsonIgnore val modelLocation: StorageLocation? = null,
    /** The institution's display name, when an agent identified one. */
    override val bankName: String? = null,
    /**
     * Page number -> bates stamp for this classification's pages. The statements and checks under it read
     * their stamps from here; their own bates columns are the Azure pipeline's.
     */
    val batesStamps: Map<Int, String> = emptyMap(),
    /** The extraction session (statement or check) that produced this classification's records. */
    override val extractionSessionId: String? = null,
    val createdAt: Long,
    val updatedAt: Long,
): IClassification {
    override val documentType: DocumentType get() = DocumentType.getBankType(classificationType)

    override val pagesOrdered: List<Int> get() = pages.sorted()

    fun toClassifiedPages() = ClassifiedPages(pages, classificationType, bankName, batesStamps)

    companion object {
        fun fromRow(row: ResultRow) = ClassificationInfo(
            classificationId = row[ClassificationsTable.id].value,
            pages = OBJECT_MAPPER.readValue(row[ClassificationsTable.pages], object : TypeReference<LinkedHashSet<Int>>() {}),
            classificationType = row[ClassificationsTable.classificationType],
            modelLocation = StorageLocation.deserialize(row[ClassificationsTable.modelLocation]),
            bankName = row[ClassificationsTable.bankName],
            batesStamps = row[ClassificationsTable.batesStamps]
                ?.let { OBJECT_MAPPER.readValue(it, object : TypeReference<Map<Int, String>>() {}) }
                ?: emptyMap(),
            extractionSessionId = row[ClassificationsTable.extractionSessionId],
            createdAt = row[ClassificationsTable.createdAt].toEpochMilli(),
            updatedAt = row[ClassificationsTable.updatedAt].toEpochMilli()
        )
    }
}

interface IClassification {
    val classificationId: UUID
    val pages: Set<Int>
    val classificationType: String
    val bankName: String?
    /** The extraction session (statement or check) that produced this classification's records. */
    val extractionSessionId: String?

    @get:JsonIgnore
    val documentType: DocumentType
    @get:JsonIgnore
    val pagesOrdered: List<Int>

}

