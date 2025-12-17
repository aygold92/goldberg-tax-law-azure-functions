package com.goldberg.law.database.service

import com.goldberg.law.database.DbExec
import com.goldberg.law.database.tables.*
import com.goldberg.law.document.exception.FileNotFoundException
import com.goldberg.law.entity.*
import com.google.common.collect.Sets
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import org.jetbrains.exposed.dao.id.EntityID
import org.jetbrains.exposed.sql.*
import org.jetbrains.exposed.sql.SqlExpressionBuilder.eq
import org.jetbrains.exposed.sql.SqlExpressionBuilder.inList
import org.jetbrains.exposed.sql.SqlExpressionBuilder.isNotNull
import java.util.*

class FileService @Inject constructor() {
    private val logger = KotlinLogging.logger {}
    /**
     * Save or get file by client and filename, return file_id
     * TODO: Update checkClientToken method in FilesTable to account for contentHash in duplicate detection
     */
    fun insertFile(inputFile: InputFile, contentHash: UUID, requestToken: UUID): UUID = DbExec.txnSafe {
        try {
            val newFile = FileEntity.new {
                this.clientId = EntityID(inputFile.clientId, ClientsTable)
                this.fileName = inputFile.info.fileName
                this.storageLocation = inputFile.info.storageLocation.serialize()
                this.contentHash = contentHash
                this.numPages = inputFile.info.numPages
            }
            logger.info { "Created new file: ${inputFile.fileName} for client: ${inputFile.clientId} with ID: $newFile.id" }
            newFile.id.value
        } catch (ex: Exception) {
            FilesTable.checkClientToken(ex, requestToken, FilesTable.clientId withValue EntityID(inputFile.clientId, ClientsTable), FilesTable.fileName withValue inputFile.fileName)
        }
    }

    // delete file and all related classifications, statements, checks, and transactions
    fun deleteInputFileWithData(fileId: UUID) = DbExec.txnSafe {

        val classificationIds = ClassificationsTable.select(ClassificationsTable.fileId eq fileId)
            .map { it[ClassificationsTable.id].value }
        val statementIds = BankStatementsTable.select(BankStatementsTable.classificationId inList classificationIds)
            .map { it[BankStatementsTable.id].value }
        val checkIds = ChecksTable.select(ChecksTable.classificationId inList classificationIds)
            .map { it[ChecksTable.id].value }
        val transactionIds = TransactionsTable.select(TransactionsTable.statementId inList statementIds)
            .map { it[TransactionsTable.id].value }

        TransactionsTable.deleteWhere { TransactionsTable.id inList transactionIds }
        BankStatementsTable.deleteWhere { BankStatementsTable.id inList statementIds }
        ChecksTable.deleteWhere { ChecksTable.id inList checkIds }
        ClassificationsTable.deleteWhere { ClassificationsTable.id inList classificationIds }
        FilesTable.deleteWhere { FilesTable.id eq fileId }
        logger.debug { "Deleted file $fileId along with ${statementIds.size} statements, ${transactionIds.size} transactions, and ${checkIds.size} checks" }
    }

    fun loadFile(fileId: UUID): InputFile = DbExec.txnSafe {
        InputFile.fromRow(
            FilesTable.leftJoin(ClientsTable, { FilesTable.id }, { ClientsTable.id })
                .select(FilesTable.id eq fileId)
                .singleOrNull() ?: throw FileNotFoundException("Could not find file $fileId")
        )
    }

    /**
     * Load files with their classifications, statements, and checks
     * Returns three lists: InputFiles (no classification), Classifications (no statement/check), and ClassifiedItems (with statement/check)
     */
    fun loadFiles(fileIds: Set<UUID>): Triple<Set<InputFile>, Set<Classification>, Set<ClassifiedItem>> = if (fileIds.isEmpty()) {
        Triple(emptySet(), emptySet(), emptySet())
    } else DbExec.txnSafe {
        // Single query with LEFT JOINs to get all data
        val rows = (FilesTable
            .leftJoin(ClassificationsTable, { FilesTable.id }, { fileId })
            .leftJoin(BankStatementsTable, { ClassificationsTable.id }, { classificationId })
            .leftJoin(ChecksTable, { ClassificationsTable.id }, { classificationId })
            .select(FilesTable.id inList fileIds))

        val inputFiles = mutableSetOf<InputFile>() // Track fileIds we've added
        val classificationSet = mutableSetOf<Classification>() // Track classificationIds we've added
        val classifiedItemSet = mutableSetOf<ClassifiedItem>() // Track statement/check IDs we've added

        val fileIdsReturned = rows.map { it[FilesTable.id].value }.toSet()
        val filesNotFound = Sets.difference(fileIds, fileIdsReturned)

        if (filesNotFound.isNotEmpty()) throw RuntimeException("The following files have not been uploaded: $filesNotFound")

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

    fun loadFileSummary(fileId: UUID): InputFileSummary = DbExec.txnSafe {
        val numChecks = ChecksTable.id.countDistinct().alias("numChecks")
        val numStatements = BankStatementsTable.id.countDistinct().alias("numStatements")
        val numTransactions = TransactionsTable.id.countDistinct().alias("numTransactions")
        val numAnalyzed = Case()
            .When(ClassificationsTable.modelLocation.isNotNull(), intLiteral(1))
            .Else(intLiteral(0))
            .sum()
            .alias("numAnalyzed")

        FilesTable
            .innerJoin(ClientsTable, { ClientsTable.id }, { FilesTable.clientId })
            .leftJoin(ClassificationsTable, { FilesTable.id }, { ClassificationsTable.fileId })
            .leftJoin(BankStatementsTable, { ClassificationsTable.id }, { BankStatementsTable.classificationId })
            .leftJoin(ChecksTable, { ClassificationsTable.id }, { ChecksTable.classificationId })
            .leftJoin(TransactionsTable, { BankStatementsTable.id }, { TransactionsTable.statementId })
            .select(ClientsTable.columns + FilesTable.columns + listOf(numChecks, numStatements, numTransactions, numAnalyzed))
            .where { FilesTable.id eq fileId }
            .groupBy(FilesTable.id)
            .map { row ->
                InputFileSummary(
                    inputFile = InputFile.fromRow(row),
                    numChecks = row[numChecks].toInt(),
                    numStatements = row[numStatements].toInt(),
                    numTransactions = row[numTransactions].toInt(),
                    numAnalyzed = row[numAnalyzed]
                )
            }.singleOrNull() ?: throw FileNotFoundException("Could not find file $fileId")
    }

    /**
     * List files with metadata using single efficient query
     */
    fun listFiles(clientId: UUID): List<InputFileSummary> = DbExec.txnSafe {
        val numChecks = ChecksTable.id.countDistinct().alias("numChecks")
        val numStatements = BankStatementsTable.id.countDistinct().alias("numStatements")
        val numTransactions = TransactionsTable.id.countDistinct().alias("numTransactions")
        val numAnalyzed = Case()
            .When(ClassificationsTable.modelLocation.isNotNull(), intLiteral(1))
            .Else(intLiteral(0))
            .sum()
            .alias("numAnalyzed")

        FilesTable
            .innerJoin(ClientsTable, { ClientsTable.id }, { FilesTable.clientId })
            .leftJoin(ClassificationsTable, { FilesTable.id }, { ClassificationsTable.fileId })
            .leftJoin(BankStatementsTable, { ClassificationsTable.id }, { BankStatementsTable.classificationId })
            .leftJoin(ChecksTable, { ClassificationsTable.id }, { ChecksTable.classificationId })
            .leftJoin(TransactionsTable, { BankStatementsTable.id }, { TransactionsTable.statementId })
            .select(ClientsTable.columns + FilesTable.columns + listOf(numChecks, numStatements, numTransactions, numAnalyzed))
            .where { FilesTable.clientId eq clientId }
            .groupBy(FilesTable.id)
            .map { row ->
                InputFileSummary(
                    inputFile = InputFile.fromRow(row),
                    numChecks = row[numChecks].toInt(),
                    numStatements = row[numStatements].toInt(),
                    numTransactions = row[numTransactions].toInt(),
                    numAnalyzed = row[numAnalyzed]
                )
            }
    }
}