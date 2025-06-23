package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.DocumentDataExtractor
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.BatesStampTable
import com.goldberg.law.document.model.input.tables.BatesStampTableRow
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.*
import com.goldberg.law.function.model.DocumentDataModelContainer
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
    private val dataExtractor: DocumentDataExtractor,
    private val dataManager: AzureStorageDataManager,
) {
    private val logger = KotlinLogging.logger {}
    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processDataModel(@DurableActivityTrigger(name = "name") input: ProcessDataModelActivityInput, context: ExecutionContext): DocumentDataModelContainer {

        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.classifiedPdfMetadata}" }
        val pdfDocument = if (!input.useOriginalFile) {
            dataManager.loadSplitPdfDocument(input.clientName, input.classifiedPdfMetadata)
        } else {
            dataManager.loadInputPdfDocument(input.clientName, input.classifiedPdfMetadata.filename)
                .docForPages(input.classifiedPdfMetadata.pages)
                .asClassifiedDocument(input.classifiedPdfMetadata.classification)
        }

        val dataModel = when (input.classifiedPdfMetadata.documentType){
            BANK, CREDIT_CARD -> dataExtractor.extractStatementData(pdfDocument);
            CHECK -> dataExtractor.extractCheckData(pdfDocument)
            else -> ExtraPageDataModel(pdfDocument.toDocumentMetadata()).also {
                logger.error { "Unable to process statement ${input.classifiedPdfMetadata} as it is not a bank, CC, or check" }
            }
        }

//         val dataModel = getStatementPage(input.pdfPageData)

        try {
            dataManager.saveModel(input.clientName, dataModel)
        } catch (ex: Throwable) {
            logger.error(ex) { "Exception writing model for ${pdfDocument.nameWithPages()}: $ex" }
        }


        return DocumentDataModelContainer(dataModel).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning model ${it.toStringDetailed()}" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "ProcessDataModelActivity"

        /**
         * The following are test statements for quick testing that doesn't require contacting the document intelligence service
         */
//        fun getStatementPage(pdfPageData: PdfPageData): DocumentDataModel {
//            val statement = statements[pdfPageData.page - 1]
//            return if (statement.isStatement()) {
//                (statement as StatementDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
//            } else if (statement.isCheck()) {
//                (statement as CheckDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
//            } else {
//                (statement as ExtraPageDataModel).copy(pageMetadata = statement.pageMetadata.copy(filename = pdfPageData.fileName, page = pdfPageData.page))
//            }
//        }

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
                pageMetadata = ClassifiedPdfMetadata(FILENAME, 1, CheckTypes.EAGLE_BANK_CHECK)
            ),
            StatementDataModel(
                date = normalizeDate("6 2 2022"),
                summaryOfAccountsTable = null,
                transactionTableDepositWithdrawal = null,
                batesStamps = BatesStampTable(listOf(BatesStampTableRow(`val` = "HERMAN-R-000630", page = 1))),
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
                pageMetadata = ClassifiedPdfMetadata(FILENAME, 2, BankTypes.WF_BANK)
            ),
            ExtraPageDataModel(ClassifiedPdfMetadata(FILENAME, 3, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(ClassifiedPdfMetadata(FILENAME, 4, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(ClassifiedPdfMetadata(FILENAME, 5, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
            ExtraPageDataModel(ClassifiedPdfMetadata(FILENAME, 6, DocumentType.IrrelevantTypes.EXTRA_PAGES)),
        )
    }
}