package com.goldberg.law.function.activity

import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.DocumentStatementCreator
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.DocumentType.*
import com.goldberg.law.entity.Classification
import com.goldberg.law.function.activity.model.ProcessDataModelActivityInput
import com.goldberg.law.function.activity.model.ProcessDataModelActivityOutput
import com.goldberg.law.function.model.ExtractedDocumentIds
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import com.google.inject.Inject

class ProcessDataModelActivity @Inject constructor(
    private val dataExtractor: DocumentDataExtractor,
    private val dataManager: AzureStorageDataManager,
    private val classificationService: ClassificationService,
    private val statementService: StatementService,
    private val checkService: CheckService,
    private val documentStatementCreator: DocumentStatementCreator,
) {
    private val logger = KotlinLogging.logger {}

    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processDataModel(@DurableActivityTrigger(name = "name") input: ProcessDataModelActivityInput, context: ExecutionContext): ProcessDataModelActivityOutput {
        val opts = input.processingOptions
        val classificationId = input.classification.classificationId

        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.classification} (options=$opts)" }

        val shouldAnalyze = opts.forceReanalysis || !input.classification.isAnalyzed()

        val documentIds = if (shouldAnalyze) {
            // analyze and create statements
            logger.info { "[${input.requestId}][${context.invocationId}] analyzing document for ${input.classification}" }
            val dataModel = analyzeModel(input.useOriginalFile, input.classification)
            createStatementsAndChecks(dataModel, input.classification, opts.replaceOnRecreate)
        } else {
            // recreate
            val existingRecords: ExtractedDocumentIds = when (input.classification.documentType) {
                BANK, CREDIT_CARD -> ExtractedDocumentIds(statementIds = statementService.loadStatementIdsForClassification(classificationId))
                CHECK -> ExtractedDocumentIds(checkIds = checkService.loadCheckIdsForClassification(classificationId))
                else -> ExtractedDocumentIds()
            }
            val shouldCreate = opts.forceRecreate || existingRecords.isEmpty()
            if (shouldCreate) {
                logger.info { "[${input.requestId}][${context.invocationId}] skipping analysis, creating statements and checks for ${input.classification}" }
                val dataModel = dataManager.loadModel(input.classification)
                createStatementsAndChecks(dataModel, input.classification, opts.replaceOnRecreate)
            } else {
                logger.info { "[${input.requestId}][${context.invocationId}] skipping ${input.classification} — already complete" }
                existingRecords
            }
        }

        return ProcessDataModelActivityOutput(input.classification.inputFile.fileId, documentIds).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning items ${it.toStringDetailed()}" }
        }
    }

    fun analyzeModel(useOriginalFile: Boolean, classification: Classification): DocumentDataModel {
        val pdfDocument = if (!useOriginalFile) {
            dataManager.loadSplitPdfDocument(classification)
        } else {
            dataManager.loadInputPdfDocument(classification.inputFile)
                .asClassifiedDocument(classification.info)
        }
        val dataModel = when (classification.documentType) {
            BANK, CREDIT_CARD -> dataExtractor.extractStatementData(pdfDocument)
            CHECK -> dataExtractor.extractCheckData(pdfDocument)
            else -> ExtraPageDataModel(pdfDocument.classification).also {
                logger.error { "Unable to process statement for $classification as it is not a bank, CC, or check" }
            }
        }

        val modelLocation = dataManager.saveModel(classification, dataModel)
        classificationService.updateModelLocation(classification.classificationId, modelLocation)
        return dataModel
    }

    fun createStatementsAndChecks(dataModel: DocumentDataModel, classification: Classification, replaceOnRecreate: Boolean): ExtractedDocumentIds {
        return when (dataModel) {
            is StatementDataModel -> {
                val statements = documentStatementCreator.createBankStatements(classification, dataModel)
                if (replaceOnRecreate) {
                    statementService.replaceStatements(classification.classificationId, statements)
                } else {
                    statements.forEach { statement -> statementService.insertBankStatementWithTransactions(statement) }
                }
                ExtractedDocumentIds(statementIds = statements.map { it.statementId }.toSet())
            }
            is CheckDataModel -> {
                val checks = dataModel.toCheckDetails()
                if (replaceOnRecreate) {
                    checkService.replaceChecks(classification, checks)
                } else {
                    checks.forEach { checkDetails -> checkService.insertCheck(classification, checkDetails) }
                }
                ExtractedDocumentIds(checkIds = checks.map { it.checkId }.toSet())
            }
            else -> throw RuntimeException("Trying to save an extra page model, this shouldn't be possible: $dataModel")
        }
    }

    companion object {
        const val FUNCTION_NAME = "ProcessDataModelActivity"
    }
}
