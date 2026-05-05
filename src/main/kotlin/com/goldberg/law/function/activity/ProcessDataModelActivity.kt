package com.goldberg.law.function.activity

import com.goldberg.law.database.service.CheckService
import com.goldberg.law.database.service.ClassificationService
import com.goldberg.law.database.service.StatementService
import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.DocumentStatementCreator
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.DocumentType.*
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

        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.classification}" }
        val pdfDocument = if (!input.useOriginalFile) {
            dataManager.loadSplitPdfDocument(input.classification)
        } else {
            dataManager.loadInputPdfDocument(input.classification.inputFile)
                .asClassifiedDocument(input.classification.info)
        }

        /** extract the data as either a statement or check*/
        val dataModel = when (input.classification.documentType){
            BANK, CREDIT_CARD -> dataExtractor.extractStatementData(pdfDocument);
            CHECK -> dataExtractor.extractCheckData(pdfDocument)
            else -> ExtraPageDataModel(pdfDocument.classification).also {
                logger.error { "Unable to process statement ${input.classification} as it is not a bank, CC, or check" }
            }
        }

        return try {
            /** update the model location */
            val modelLocation = dataManager.saveModel(input.classification, dataModel)
            classificationService.updateModelLocation(input.classification.classificationId, modelLocation)

            /** convert the model to a statement or check */
            val documentIds = when (dataModel) {
                is StatementDataModel -> {
                    val statements = documentStatementCreator.createBankStatements(input.classification, dataModel)
                    statements.forEach { statement -> statementService.insertBankStatementWithTransactions(statement) }
                    ExtractedDocumentIds(statementIds = statements.map { it.statementId }.toSet())
                }

                is CheckDataModel -> {
                    val checks = dataModel.toCheckDetails()
                    checks.forEach { checkDetails -> checkService.insertCheck(input.classification, checkDetails) }
                    ExtractedDocumentIds(checkIds = checks.map { it.checkId }.toSet())
                }

                else -> {
                    throw RuntimeException("Trying to save an extra page model, this shouldn't be possible: $dataModel")
                }
            }
            ProcessDataModelActivityOutput(
                input.classification.inputFile.fileId,
                documentIds
            )
        } catch (ex: Throwable) {
            logger.error(ex) { "Exception saving model for ${pdfDocument.classification}: $ex" }
            throw ex
        }.also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning items ${it.toStringDetailed()}" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "ProcessDataModelActivity"
    }
}