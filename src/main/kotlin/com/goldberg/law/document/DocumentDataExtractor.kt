package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.CheckDataModel.Companion.toCheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel.Companion.toBankDocument
import com.goldberg.law.document.model.output.*
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject

class DocumentDataExtractor @Inject constructor(
    @Inject private val client: DocumentAnalysisClient,
    private val statementExtractorModelId: String,
    private val checkExtractorModelId: String,
) {
    private val logger = KotlinLogging.logger {}

    fun extractTransactionHistory(classifiedDocuments: List<ClassifiedPdfDocument>): Collection<BankStatement> {
        val statements: MutableMap<BankStatementKey, BankStatement> = mutableMapOf()

        // keep track of the last page, as we may need to assume certain information based on them being in sequential order
        var lastUsedStatement: BankStatement? = null
        classifiedDocuments.forEach { classifiedDocument ->
            try {
                logger.info { "[Transaction History] Processing $classifiedDocument" }
                val analyzedBankDocument = extractData(classifiedDocument, statementExtractorModelId).toBankDocument()
                /**
                 * Choose the page to use as the following:
                 * 1. if we have the statementDate/accountNumber from the page, use that
                 * 2. find the first statement that starts on a page less than the current page number
                 * 3. just pick the first one in the summary
                 */
                val accountNumber = analyzedBankDocument.accountNumber ?:
                analyzedBankDocument.pageNum?.let { analyzedBankDocument.summaryOfAccounts?.findRelevantAccount(it) }?.accountNumber ?:
                lastUsedStatement?.accountNumber

                val statementDate = analyzedBankDocument.statementDate ?: lastUsedStatement?.statementDate


                val statementKey = BankStatementKey(statementDate, accountNumber)

                logger.info { "[Transaction History] Found $statementKey, page ${analyzedBankDocument.pageNum} (${analyzedBankDocument.batesStamp})" }

                // process the summary of accounts table (for WF Bank)
                analyzedBankDocument.summaryOfAccounts?.records?.forEach { account ->
                    val accountStatementKey = BankStatementKey(statementDate, account.accountNumber)
                    statements[accountStatementKey] = statements.getOrDefault(
                        accountStatementKey,
                        BankStatement(classifiedDocument.name, classifiedDocument.classification, accountStatementKey, account.startPage)
                    ).update(
                        beginningBalance = account.beginningBalance,
                        endingBalance = account.endingBalance
                    )
                }

                val metadata = TransactionHistoryPageMetadata(
                    filename = classifiedDocument.name,
                    filePageNumber = classifiedDocument.firstPage,
                    statementDate = statementDate,
                    statementPageNum = analyzedBankDocument.pageNum,
                    batesStamp = analyzedBankDocument.batesStamp
                )

                val thRecords = analyzedBankDocument.getTransactionRecords(statementDate?.getYearSafe(), metadata)

                statements[statementKey] = statements.getOrDefault(statementKey, BankStatement(classifiedDocument.name, classifiedDocument.classification, statementKey)).update(
                    accountNumber = analyzedBankDocument.accountNumber,
                    totalPages = analyzedBankDocument.totalPages,
                    beginningBalance = analyzedBankDocument.beginningBalance,
                    endingBalance = analyzedBankDocument.endingBalance,
                    interestCharged = analyzedBankDocument.interestCharged,
                    feesCharged = analyzedBankDocument.feesCharged,
                    transactions = thRecords
                ).update(documentType = analyzedBankDocument.getStatementType())

                lastUsedStatement = statements[statementKey]
            } catch (e: Throwable) {
                logger.error(e) { "Exception processing $classifiedDocument: $e" }
            }
        }
        return statements.values
    }

    fun extractCheckData(classifiedDocuments: List<ClassifiedPdfDocument>): Map<CheckDataKey, CheckData> {
        return classifiedDocuments.mapNotNull { classifiedDocument ->
            try {
                logger.info { "[Check] Processing file page $classifiedDocument" }
                val checkDataModel = extractData(classifiedDocument, checkExtractorModelId).toCheckDataModel()
                logger.info { "[Check] Found $checkDataModel" }
                checkDataModel.toCheckData(PageMetadata(checkDataModel.batesStamp, classifiedDocument.name, classifiedDocument.firstPage))
            } catch (e: Throwable) {
                logger.error(e) { "Exception processing $classifiedDocument: $e" }
                null
            }
        }.associateBy { it.checkDataKey }
    }

    private fun extractData(pdfDocument: PdfDocument, modelId: String): AnalyzedDocument {
        val poller = retryWithBackoff(
            { client.beginAnalyzeDocument(modelId, pdfDocument.toBinaryData()) },
            ::isAzureThrottlingError
        )

        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        logger.debug { result.toStringDetailed() }

        val documents = poller.finalResult.documents
        if (documents.size != 1) {
            logger.error { "${pdfDocument.nameWithPage} returned ${documents.size} analyzed pages" }
        }

        return documents[0].also { logger.trace { it.toStringDetailed() } }
    }
}