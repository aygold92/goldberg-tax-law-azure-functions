package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.goldberg.law.document.exception.BadDataException
import com.goldberg.law.document.model.TransactionHistoryPage
import com.goldberg.law.document.model.TransactionHistoryPage.Companion.PAGE_NUM
import com.goldberg.law.document.model.TransactionHistoryPage.Companion.STATEMENT_DATE
import com.goldberg.law.document.model.TransactionHistoryPage.Companion.STATEMENT_YEAR
import com.goldberg.law.document.model.TransactionHistoryPage.Companion.TOTAL_PAGES
import com.goldberg.law.document.model.TransactionHistoryPage.Companion.TRANSACTION_TABLE
import com.goldberg.law.document.model.TransactionHistoryRecord
import com.goldberg.law.document.model.TransactionHistoryRecord.Companion.CHECK_NUMBER
import com.goldberg.law.document.model.TransactionHistoryRecord.Companion.DATE
import com.goldberg.law.document.model.TransactionHistoryRecord.Companion.DEPOSITS_ADDITIONS
import com.goldberg.law.document.model.TransactionHistoryRecord.Companion.DESCRIPTION
import com.goldberg.law.document.model.TransactionHistoryRecord.Companion.WITHDRAWALS_SUBTRACTIONS
import com.goldberg.law.pdf.model.ClassifiedPdfDocument
import com.goldberg.law.util.*
import javax.inject.Inject
import javax.inject.Named

class DocumentDataExtractor @Inject constructor(
    @Inject private val client: DocumentAnalysisClient,
    @Named("CustomDataModelId") private val modelId: String
) {
    fun extractTransactionHistory(document: ClassifiedPdfDocument): List<TransactionHistoryPage> {
        val poller = retryWithBackoff(
            { client.beginAnalyzeDocument(modelId, document.toBinaryData()) },
            ::isAzureThrottlingError
        )

        // this should already have exponential backoff, but *shrug*
        val result = retryWithBackoff(poller::waitForCompletion, ::isAzureThrottlingError)
        println(result.toStringDetailed())

        return poller.finalResult.documents.mapIndexed { documentIdx, analyzedDocument ->
            val statementDate = fromStatementDate(analyzedDocument.fields[STATEMENT_DATE]?.valueAsString,
                analyzedDocument.fields[STATEMENT_YEAR]?.valueAsString)
            val pageNum = analyzedDocument.fields[PAGE_NUM]?.valueAsString?.toInt()
            val table = analyzedDocument.fields[TRANSACTION_TABLE]?.valueAsList
                ?: throw BadDataException(TRANSACTION_TABLE, statementDate, pageNum)

            val thRecords = table.map { record ->
                val fieldMap = record.valueAsMap
                val (checkNumber, description) = getCheckNumberAndDescription(fieldMap)
                TransactionHistoryRecord(
                    // TODO: fix model so it returns it in value
                    date = fromTransactionDate(fieldMap[DATE]?.content, statementDate?.getYearSafe()),
                    checkNumber = checkNumber,
                    description = description,
                    depositAmount = fieldMap[DEPOSITS_ADDITIONS]?.valueAsDouble,
                    withdrawalAmount = fieldMap[WITHDRAWALS_SUBTRACTIONS]?.valueAsDouble
                )
            }

            TransactionHistoryPage(
                filename = document.name,
                // note: if the documents are multiple pages, I may not be able to assume this lines up
                filePageNumber = document.pages[documentIdx],
                statementDate = statementDate,
                statementPageNum = pageNum,
                statementTotalPages = analyzedDocument.fields[TOTAL_PAGES]?.valueAsString?.toInt(),
                transactionHistoryRecords = thRecords
            )
        }
    }

    /**
     * Some bank statements merge the CheckNumber and Description field into one
     */
    private fun getCheckNumberAndDescription(fieldMap: Map<String, DocumentField>): Pair<Int?, String?> {
        val checkContent = fieldMap[CHECK_NUMBER]?.content?.trim()
        val descriptionValue = fieldMap[DESCRIPTION]?.valueAsString?.trim()
        return if (checkContent?.matches(NUMBER_CHECK_REGEX) == true) {
            NUMBER_CHECK_REGEX.find(checkContent)!!.destructured.let {
                Pair(it.component1().toInt(), it.component2())
            }
        } else if (descriptionValue?.matches(NUMBER_CHECK_REGEX) == true) {
            NUMBER_CHECK_REGEX.find(descriptionValue)!!.destructured.let {
                Pair(it.component1().toInt(), it.component2())
            }
        } else {
            Pair(fieldMap[CHECK_NUMBER]?.valueAsLong?.toInt(), descriptionValue)
        }
    }

    companion object {
        // don't need ^ and $ in regex and end because matches() assumes that already
        val NUMBER_CHECK_REGEX = "(\\d+)(check)".toRegex(RegexOption.IGNORE_CASE)
    }
}