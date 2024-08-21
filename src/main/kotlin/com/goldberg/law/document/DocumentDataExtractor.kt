package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.StatementDataModel.Companion.toBankDocument
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.document.model.output.TransactionHistoryPage
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.pdf.model.StatementType
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import javax.inject.Inject
import javax.inject.Named

class DocumentDataExtractor @Inject constructor(
    @Inject private val client: DocumentAnalysisClient,
    @Named("CustomDataModelId") private val modelId: String
) {
    private val logger = KotlinLogging.logger {}

    fun extractTransactionHistory(classifiedDocuments: List<ClassifiedPdfDocument>): Collection<BankStatement> {
        val statements: MutableMap<BankStatementKey, BankStatement> = mutableMapOf()

        // keep track of the last page, as we may need to assume certain information based on them being in sequential order
        var lastUsedStatement: BankStatement? = null
        classifiedDocuments.forEach { classifiedDocument ->
            try {

                val analyzedBankDocument = classifiedDocument.extractData().toBankDocument()
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

                val statementKey = BankStatementKey(statementDate, accountNumber)

                logger.info { "Processing statement $statementKey, page ${analyzedBankDocument.pageNum} " }

                val thRecords = analyzedBankDocument.getTransactionRecords(statementDate?.getYearSafe())

                val thPage = TransactionHistoryPage(
                    filename = classifiedDocument.name,
                    filePageNumber = classifiedDocument.firstPage,
                    statementDate = statementDate,
                    statementPageNum = analyzedBankDocument.pageNum,
                    batesStamp = analyzedBankDocument.batesStamp,
                    transactionHistoryRecords = thRecords
                )

                statements[statementKey] = statements.getOrDefault(statementKey, BankStatement(classifiedDocument.name, classifiedDocument.classification, statementKey)).update(
                    statementType = classifiedDocument.statementType,
                    accountNumber = analyzedBankDocument.accountNumber,
                    totalPages = analyzedBankDocument.totalPages,
                    beginningBalance = analyzedBankDocument.beginningBalance,
                    endingBalance = analyzedBankDocument.endingBalance,
                    interestCharged = analyzedBankDocument.interestCharged,
                    feesCharged = analyzedBankDocument.feesCharged,
                    thPage = thPage
                ).update(statementType = analyzedBankDocument.getStatementType())

                lastUsedStatement = statements[statementKey]
            } catch (e: Throwable) {
                logger.error(e) { "Exception processing $classifiedDocument: $e" }
            }
        }
        return statements.values
    }

    private fun PdfDocument.extractData(): AnalyzedDocument {
        val poller = retryWithBackoff(
            { client.beginAnalyzeDocument(modelId, this.toBinaryData()) },
            ::isAzureThrottlingError
        )

        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        logger.debug { result.toStringDetailed() }

        val documents = poller.finalResult.documents
        if (documents.size != 1) {
            logger.error { "$nameWithPage returned ${documents.size} analyzed pages" }
        }

        return documents[0].also { logger.trace { it.toStringDetailed() } }
    }
}