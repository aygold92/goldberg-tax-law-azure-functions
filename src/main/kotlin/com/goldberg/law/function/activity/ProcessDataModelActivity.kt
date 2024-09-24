package com.goldberg.law.function.activity

import com.goldberg.law.PdfExtractorMain
import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.document.model.input.*
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
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
    private val pdfExtractorMain: PdfExtractorMain,
    private val dataManager: DataManager,
) {
    private val logger = KotlinLogging.logger {}
    /**
     * This is the activity function that is invoked by the orchestrator function.
     */
    @FunctionName(FUNCTION_NAME)
    fun processDataModelActivity(@DurableActivityTrigger(name = "name") input: ProcessDataModelActivityInput, context: ExecutionContext): DocumentDataModelContainer {

        logger.info { "[${input.requestId}][${context.invocationId}] processing ${input.pdfPageData}" }
        val pdfDocument = dataManager.loadSplitPdfDocumentPage(input.pdfPageData)
        val dataModel = pdfExtractorMain.processDocument(pdfDocument, input.overrideTypeClassification, null)
        return DocumentDataModelContainer(dataModel).also {
            logger.info { "[${input.requestId}][${context.invocationId}] returning model ${it.toStringDetailed()}" }
        }
    }

    companion object {
        const val FUNCTION_NAME = "ProcessDataModelActivity"

        const val FILENAME = "test.pdf"

        val statements = listOf(
            CheckDataModel(
                batesStamp = "HERMAN-R-000629",
                accountNumber = null,
                to = "Some Guy",
                checkNumber = 400,
                amount = 50.asCurrency(),
                date = normalizeDate("6 2 2022"),
                description = "test",
                pageMetadata = PdfDocumentPageMetadata(FILENAME, 1, BankTypes.WF_BANK)
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
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 1, BankTypes.WF_BANK)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 2, BankTypes.WF_BANK)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 3, BankTypes.WF_BANK)),
            ExtraPageDataModel(PdfDocumentPageMetadata(FILENAME, 4, BankTypes.WF_BANK)),
        )
    }
}