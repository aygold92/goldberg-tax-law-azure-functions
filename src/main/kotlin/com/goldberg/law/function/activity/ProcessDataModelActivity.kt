package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentClassifier
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.DocumentDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.function.model.DocumentDataModelContainer
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate
import com.goldberg.law.util.toStringDetailed
import com.microsoft.azure.functions.ExecutionContext
import com.microsoft.azure.functions.annotation.FunctionName
import com.microsoft.durabletask.azurefunctions.DurableActivityTrigger
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class ProcessDataModelActivity @Inject constructor(
    private val classifier: DocumentClassifier,
    private val dataExtractor: DocumentDataExtractor,
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}
    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processDataModel(@DurableActivityTrigger(name = "name") input: ProcessDataModelActivityInput, context: ExecutionContext): DocumentDataModelContainer {

        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.pdfPageData}" }
        val pdfDocument = dataManager.loadSplitPdfDocumentPage(input.clientName, input.pdfPageData)
        val dataModel = processDocument(pdfDocument, input.overrideTypeClassification)

//         val dataModel = getStatementPage(input.pdfPageData)

        try {
            dataManager.saveModel(input.clientName, dataModel)
        } catch (ex: Throwable) {
            logger.error(ex) { "Exception writing model for ${pdfDocument.nameWithPage()}: $ex" }
        }
        return DocumentDataModelContainer(dataModel).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning model ${it.toStringDetailed()}" }
        }
    }

    private fun processDocument(inputDocument: PdfDocumentPage, classifiedTypeOverride: String?): DocumentDataModel {
        val classifiedDocument = if (classifiedTypeOverride != null) {
            logger.info { "Overriding classification as $classifiedTypeOverride as requested..." }
            inputDocument.withType(classifiedTypeOverride)
        } else {
            classifier.classifyDocument(inputDocument)
        }

        return when {
            classifiedDocument.isStatementDocument() -> dataExtractor.extractStatementData(classifiedDocument);
            classifiedDocument.isRelevant() -> dataExtractor.extractCheckData(classifiedDocument)
            else -> ExtraPageDataModel(classifiedDocument.toDocumentMetadata())
        }
    }

    companion object {
        const val FUNCTION_NAME = "ProcessDataModelActivity"

        /**
         * The following are test statements for quick testing that doesn't require contacting the document intelligence service
         */
        fun getStatementPage(pdfPageData: PdfPageData): DocumentDataModel {
            val statement = statements[pdfPageData.page - 1]
            return if (statement.isStatement()) {
                (statement as StatementDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
            } else if (statement.isCheck()) {
                (statement as CheckDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
            } else {
                (statement as ExtraPageDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
            }
        }

        const val FILENAME = "test.pdf"

        val statements = listOf(
            CheckDataModel(
                batesStamp = "HERMAN-R-000629",
                accountNumber = "3443",
                to = "Some Guy",
                checkNumber = 400,
                amount = 50.asCurrency(),
                date = normalizeDate("6 2 2022"),
                description = "test",
                checkEntries = null,
                pageMetadata = PdfDocumentPageMetadata(FILENAME, 1, DocumentType.CheckTypes.EAGLE_BANK_CHECK)
            ),
            StatementDataModel(
                bankIdentifier = "WELLS FARGO",
                date = normalizeDate("6 2 2022"),
                pageNum = 2,
                totalPages = 7,
                summaryOfAccountsTable = null,
                transactionTableDepositWithdrawal = null,
                batesStamp = "HERMAN-R-000630",
                accountNumber = "3443",
                beginningBalance = 0.07.asCurrency(),
                endingBalance = 48262.17.asCurrency(),
                transactionTableAmount = null,
                transactionTableCreditsCharges = null,
                transactionTableDebits = null,
                transactionTableCredits = null,
                transactionTableChecks = null,
                interestCharged = null,
                feesCharged = null,
                pageMetadata = PdfDocumentPageMetadata(FILENAME, 1, BankTypes.WF_BANK)
            ),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 2, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 3, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 4, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
        )
    }
}