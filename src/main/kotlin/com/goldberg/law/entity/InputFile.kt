package com.goldberg.law.entity

import com.goldberg.law.database.tables.FilesTable
import org.jetbrains.exposed.sql.ResultRow
import java.util.*

data class InputFile(
    val client: Client,
    val info: InputFileInfo
): IInputFile by info, IClient by client {
    fun withFileId(fileId: UUID) = copy(info = info.copy(fileId = fileId))

    companion object {
        fun fromRow(row: ResultRow) = InputFile(
            client = Client.fromRow(row),
            info = InputFileInfo.fromRow(row)
        )
    }
}

data class InputFileInfo(
    override val fileId: UUID,
    override val fileName: String,
    override val numPages: Int,
    val contentHash: UUID,
    val uploadedAt: Long,
    override val anthropicFileId: String? = null,
    /** The splitting-agent session that classified this file. */
    override val splitterSessionId: String? = null,
): IInputFile {
    companion object {
        fun fromRow(row: ResultRow) = InputFileInfo(
            fileId = row[FilesTable.id].value,
            fileName = row[FilesTable.fileName],
            numPages = row[FilesTable.numPages],
            contentHash = row[FilesTable.contentHash],
            uploadedAt = row[FilesTable.uploadedAt].toEpochMilli(),
            anthropicFileId = row[FilesTable.anthropicFileId],
            splitterSessionId = row[FilesTable.splitterSessionId],
        )
    }
}

interface IInputFile {
    val fileId: UUID
    val fileName: String
    val numPages: Int

    /** The Anthropic Files API id of the uploaded bundle; null until the file has been sent to an agent. */
    val anthropicFileId: String?
    /** The splitting-agent session that classified this file. */
    val splitterSessionId: String?
}
