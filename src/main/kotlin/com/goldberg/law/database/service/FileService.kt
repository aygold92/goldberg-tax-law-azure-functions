package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec.txnSafe
import com.goldberg.law.database.tables.*
import com.goldberg.law.document.exception.EntityNotFoundException
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.entity.*
import com.google.common.collect.Sets
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import java.util.*

class FileService @Inject constructor(private val db: Database) {
    private val logger = KotlinLogging.logger {}
    /** Save or get file by client and filename, return file_id */
    fun insertFile(inputFile: InputFile, requestToken: UUID): UUID = db.txnSafe {
        try {
            val newFileId = FilesTable.insert {
                it[FilesTable.clientId] = EntityID(inputFile.clientId, ClientsTable)
                it[FilesTable.fileName] = inputFile.info.fileName
                it[FilesTable.contentHash] = inputFile.info.contentHash
                it[FilesTable.numPages] = inputFile.info.numPages
                it[FilesTable.clientToken] = requestToken
            }[FilesTable.id].value
            logger.info { "Created new file: ${inputFile.fileName} for client: ${inputFile.clientId} with ID: $newFileId" }
            newFileId
        } catch (ex: Exception) {
            FilesTable.checkClientToken(ex,
                requestToken,
                listOf(
                    FilesTable.clientId withValue EntityID(inputFile.clientId, ClientsTable),
                    FilesTable.fileName withValue inputFile.fileName
                ) to "filename \"${inputFile.fileName}\" already exists", listOf(
                    FilesTable.clientId withValue EntityID(inputFile.clientId, ClientsTable),
                    FilesTable.contentHash withValue inputFile.info.contentHash
                ) to "the same file has already been uploaded"
            )
        }
    }

    // delete file and all related classifications, statements, checks, and transactions
    fun deleteInputFile(fileId: UUID) = db.txnSafe {
        FilesTable.deleteWhere { FilesTable.id eq fileId }.also {
            logger.info { if (it > 0) "Deleted file [$fileId]" else "Delete requested but not found for file [$fileId]" }
        }
    }

    fun loadFile(fileId: UUID): InputFile = db.txnSafe {
        InputFile.fromRow(
            FilesTable.joinClients()
                .selectAll().where { FilesTable.id eq fileId }
                .singleOrNull() ?: throw FileNotFoundException("Could not find file $fileId")
        )
    }

    /**
     * Load files with their classifications, statements, and checks
     * Returns three lists: InputFiles (no classification), Classifications (no statement/check), and ClassifiedItems (with statement/check)
     */
    fun loadFilesToProcess(fileIds: Set<UUID>): Triple<Set<InputFile>, Set<Classification>, Set<ClassifiedItem>> = if (fileIds.isEmpty()) {
        Triple(emptySet(), emptySet(), emptySet())
    } else db.txnSafe {
        // Single query with LEFT JOINs to get all data
        val rows = FilesTable.joinStatementsAndChecks()
            .selectAll().where { FilesTable.id inList fileIds }

        val inputFiles = mutableSetOf<InputFile>() // Track fileIds we've added
        val classificationSet = mutableSetOf<Classification>() // Track classificationIds we've added
        val classifiedItemSet = mutableSetOf<ClassifiedItem>() // Track statement/check IDs we've added

        val fileIdsReturned = rows.map { it[FilesTable.id].value }.toSet()

        val filesNotFound = Sets.difference(fileIds, fileIdsReturned)
        if (filesNotFound.isNotEmpty()) throw EntityNotFoundException(EntityType.InputFile, filesNotFound.toList())

        rows.forEach { row ->
            if (row.getOrNull(ChecksTable.id)?.value != null) {
                classifiedItemSet.add(ClassifiedCheck.fromRow(row))
            } else if (row.getOrNull(BankStatementsTable.id)?.value != null) {
                classifiedItemSet.add(ClassifiedStatement.fromRow(row))
            } else if (row.getOrNull(ClassificationsTable.id)?.value != null) {
                classificationSet.add(Classification.fromRow(row))
            } else {
                inputFiles.add(InputFile.fromRow(row))
            }
        }

        Triple(inputFiles, classificationSet, classifiedItemSet)
    }

    fun fileNamesExist(clientId: UUID, fileNames: List<String>): Set<String> = db.txnSafe {
        FilesTable.select(FilesTable.fileName)
            .where { (FilesTable.clientId eq clientId) and (FilesTable.fileName inList fileNames) }
            .map { it[FilesTable.fileName] }
            .toSet()
    }

    fun loadFileSummary(fileId: UUID): InputFileSummary = db.txnSafe {
        queryFileSummaries { FilesTable.id eq fileId }
            .singleOrNull() ?: throw FileNotFoundException("Could not find file $fileId")
    }

    /**
     * List files with metadata using single efficient query
     */
    fun listFileSummaries(clientId: UUID): List<InputFileSummary> = db.txnSafe {
        queryFileSummaries { FilesTable.clientId eq clientId }
    }

    private fun queryFileSummaries(where: SqlExpressionBuilder.() -> Op<Boolean>): List<InputFileSummary> {
        val numChecks = ChecksTable.id.countDistinct().alias("numChecks")
        val numStatements = BankStatementsTable.id.countDistinct().alias("numStatements")
        val numTransactions = TransactionsTable.id.countDistinct().alias("numTransactions")
        val numDocuments = ClassificationsTable.id.countDistinct().alias("numDocuments")
        val numAnalyzed = object : org.jetbrains.exposed.sql.Function<Long>(LongColumnType()) {
            override fun toQueryBuilder(queryBuilder: QueryBuilder) = queryBuilder {
                append("COUNT(DISTINCT CASE WHEN ")
                append(ClassificationsTable.modelLocation)
                append(" IS NOT NULL THEN ")
                append(ClassificationsTable.id)
                append(" END)")
            }
        }.alias("numAnalyzed")

        return FilesTable.joinStatementsAndChecks()
            .leftJoin(TransactionsTable, { BankStatementsTable.id }, { TransactionsTable.statementId })
            .select(ClientsTable.columns + FilesTable.columns + listOf(numChecks, numStatements, numTransactions, numAnalyzed, numDocuments))
            .where(where)
            .groupBy(FilesTable.id)
            .orderBy(FilesTable.uploadedAt to SortOrder.DESC)
            .map { row ->
                InputFileSummary(
                    inputFile = InputFile.fromRow(row),
                    numChecks = row[numChecks].toInt(),
                    numStatements = row[numStatements].toInt(),
                    numTransactions = row[numTransactions].toInt(),
                    numAnalyzed = row[numAnalyzed].toInt(),
                    numDocuments = row[numDocuments].toInt(),
                )
            }
    }

    companion object {
        fun FilesTable.joinClients() = innerJoin(ClientsTable, { ClientsTable.id }, { FilesTable.clientId })

        fun FilesTable.joinStatementsAndChecks() = joinClients()
        .leftJoin(ClassificationsTable, { FilesTable.id }, { ClassificationsTable.fileId })
        .leftJoin(BankStatementsTable, { ClassificationsTable.id }, { BankStatementsTable.classificationId })
        .leftJoin(ChecksTable, { ClassificationsTable.id }, { ChecksTable.classificationId })
    }
}