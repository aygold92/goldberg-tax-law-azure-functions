package com.goldberg.law.document

import com.goldberg.law.document.model.JointStatementAccountSummary
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DocumentStatementCreator {
    private val logger = KotlinLogging.logger {}

    fun createBankStatements(models: Collection<StatementDataModel>): Collection<BankStatement> {
        val sortedModels = models.sortedWith(
            compareBy<StatementDataModel> { it.pageMetadata.filename }.thenBy { it.pageMetadata.page })

        val statements: MutableMap<BankStatementKey, BankStatement> = mutableMapOf()

        var lastJointStatementSummary: JointStatementAccountSummary? = null
        // keep track of the last page, as we may need to assume certain information based on them being in sequential order
        var lastUsedStatement: BankStatement? = null
        sortedModels.forEach { analyzedBankDocument ->
            try {
                logger.info { "[Transaction History] Processing ${analyzedBankDocument.pageMetadata}" }

                val couldNotBeLastUsedStatement = lastUsedStatement?.primaryKey?.couldNotMatch(analyzedBankDocument) ?: true

                val statementDate: Date? = analyzedBankDocument.statementDate
                    ?: if (couldNotBeLastUsedStatement) null
                    else lastUsedStatement?.statementDate

                if (statementDate != null && statementDate != lastJointStatementSummary?.statementDate) {
                    lastJointStatementSummary = null
                }
                /**
                 * Choose the account number to use as the following:
                 * 1. if we have the accountNumber on the page, use that
                 * 2. check if the last joint account summary matches and if so select the relevant account from the summary
                 * 3. use the account number from the last used statement
                 */
                val accountNumber = analyzedBankDocument.accountNumber
                    ?: lastJointStatementSummary?.findRelevantAccountIfMatches(statementDate, analyzedBankDocument.pageMetadata.classification, analyzedBankDocument.pageNum)
                    ?: if (couldNotBeLastUsedStatement)
                        null
                    else lastUsedStatement?.accountNumber

                val statementKey = BankStatementKey(statementDate?.toTransactionDate(), accountNumber, analyzedBankDocument.pageMetadata.classification)

                logger.info { "[Transaction History] Found $statementKey, page ${analyzedBankDocument.pageNum} (${analyzedBankDocument.batesStamp})" }

                // process the summary of accounts table (for WF Bank)
                analyzedBankDocument.summaryOfAccountsTable?.let { summaryOfAccountsTable ->
                    summaryOfAccountsTable.records.forEach { account ->
                        val accountStatementKey = BankStatementKey(statementDate?.toTransactionDate(), account.accountNumber, analyzedBankDocument.pageMetadata.classification)
                        statements[accountStatementKey] = statements.getOrDefault(
                            accountStatementKey,
                            BankStatement(analyzedBankDocument.pageMetadata.filename, analyzedBankDocument.pageMetadata.classification, accountStatementKey, account.startPage)
                        ).update(
                            beginningBalance = account.beginningBalance,
                            endingBalance = account.endingBalance
                        )
                    }
                    // if statementDate was null, the account summary would be useless
                    if (statementDate != null) {
                        lastJointStatementSummary = JointStatementAccountSummary(statementDate, analyzedBankDocument.pageMetadata.classification, summaryOfAccountsTable)
                    }
                }

                val metadata = TransactionHistoryPageMetadata(
                    filename = analyzedBankDocument.pageMetadata.filename,
                    filePageNumber = analyzedBankDocument.pageMetadata.page,
                    date = statementDate?.toTransactionDate(),
                    statementPageNum = analyzedBankDocument.pageNum,
                    batesStamp = analyzedBankDocument.batesStamp
                )

                val thRecords = analyzedBankDocument.getTransactionRecords(statementDate, metadata)

                statements[statementKey] = statements.getOrDefault(statementKey,
                    BankStatement(analyzedBankDocument.pageMetadata.filename, analyzedBankDocument.pageMetadata.classification, statementKey)).update(
                        accountNumber = analyzedBankDocument.accountNumber,
                        totalPages = analyzedBankDocument.totalPages,
                        beginningBalance = analyzedBankDocument.beginningBalance,
                        endingBalance = analyzedBankDocument.endingBalance,
                        interestCharged = analyzedBankDocument.interestCharged,
                        feesCharged = analyzedBankDocument.feesCharged,
                        transactions = thRecords,
                        pageMetadata = metadata
                ).update(documentType = analyzedBankDocument.getStatementType())

                lastUsedStatement = statements[statementKey]
            } catch (e: Throwable) {
                logger.error(e) { "Exception processing $analyzedBankDocument: $e" }
            }
        }
        return statements.values
    }

    companion object {
        fun BankStatementKey.couldNotMatch(statementModel: StatementDataModel): Boolean {
            return statementModel.date != null && statementModel.statementDate != this.statementDate ||
                    statementModel.accountNumber != null && accountNumber != this.accountNumber ||
                    statementModel.pageMetadata.classification != this.classification
        }
    }
}