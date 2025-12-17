package com.goldberg.law.entity

import com.goldberg.law.database.tables.FilesTable
import com.goldberg.law.datamanager.StorageLocation
import net.minidev.json.annotate.JsonIgnore
import org.jetbrains.exposed.sql.ResultRow
import java.util.*

data class InputFile(
    val client: Client,
    val info: InputFileInfo
): IInputFile by info, IClient by client {
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
    override val storageLocation: StorageLocation,
    override val uploadedAt: Long,
    override val numPages: Int
): IInputFile {
    companion object {
        fun fromRow(row: ResultRow) = InputFileInfo(
            fileId = row[FilesTable.id].value,
            fileName = row[FilesTable.fileName],
            storageLocation = StorageLocation.deserialize(row[FilesTable.storageLocation])!!,
            uploadedAt = row[FilesTable.uploadedAt].epochSecond,
            numPages = row[FilesTable.numPages]
        )
    }
}

interface IInputFile {
    val fileId: UUID
    val fileName: String
    val storageLocation: StorageLocation
    val uploadedAt: Long
    val numPages: Int
}
