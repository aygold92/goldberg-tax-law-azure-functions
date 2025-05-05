package com.goldberg.law.document

import com.goldberg.law.document.model.JointStatementAccountSummary
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.toTransactionDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

/**
 *
 * Hack for NFCU Bank: <TODO: fill in explanation>
 */
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

                // if its a new statement date or classification, clear the summary
                if (statementDate != null && statementDate != lastJointStatementSummary?.statementDate || analyzedBankDocument.pageMetadata.classification != lastJointStatementSummary?.classification) {
                    lastJointStatementSummary = null
                }

                // process the summary of accounts table (for WF Bank or NFCU Bank)
                analyzedBankDocument.summaryOfAccountsTable?.let { summaryOfAccountsTable ->
                    summaryOfAccountsTable.records.forEach { account ->
                        val accountStatementKey = BankStatementKey(statementDate?.toTransactionDate(), account.accountNumber, analyzedBankDocument.pageMetadata.classification)
                        statements[accountStatementKey] = statements.getOrDefault(
                            accountStatementKey,
                            BankStatement(analyzedBankDocument.pageMetadata.filename, analyzedBankDocument.pageMetadata.classification, accountStatementKey, account.startPage)
                        ).update(
                            beginningBalance = account.beginningBalance,
                            endingBalance = account.endingBalance,
                            documentType = DocumentType.BANK
                        )
                    }
                    // if statementDate was null, the account summary would be useless
                    lastJointStatementSummary = if (statementDate != null)
                        JointStatementAccountSummary(statementDate, analyzedBankDocument.pageMetadata.classification, summaryOfAccountsTable)
                    else null
                }


                val accountNumber: String? = if (analyzedBankDocument.pageMetadata.classification == DocumentType.BankTypes.NFCU_BANK && !analyzedBankDocument.isManuallyOverriden()) {
                    chooseAccountNumberForNFCUBank(statementDate, analyzedBankDocument, lastUsedStatement, lastJointStatementSummary)
                } else {
                    chooseAccountNumber(statementDate, analyzedBankDocument, lastUsedStatement, lastJointStatementSummary)
                }

                val statementKey = BankStatementKey(statementDate?.toTransactionDate(), accountNumber, analyzedBankDocument.pageMetadata.classification)

                logger.info { "[Transaction History] Found $statementKey, page ${analyzedBankDocument.pageNum} (${analyzedBankDocument.batesStamp})" }

                val metadata = TransactionHistoryPageMetadata(
                    filePageNumber = analyzedBankDocument.pageMetadata.page,
                    statementPageNum = analyzedBankDocument.pageNum,
                    batesStamp = analyzedBankDocument.batesStamp
                )

                val thRecords = analyzedBankDocument.getTransactionRecords(statementDate, metadata)

                statements[statementKey] = statements.getOrDefault(statementKey,
                    BankStatement(analyzedBankDocument.pageMetadata.filename, analyzedBankDocument.pageMetadata.classification, statementKey)).update(
                        accountNumber = accountNumber,
                        totalPages = analyzedBankDocument.totalPages,
                        beginningBalance = analyzedBankDocument.beginningBalance,
                        endingBalance = analyzedBankDocument.endingBalance,
                        interestCharged = analyzedBankDocument.interestCharged,
                        feesCharged = analyzedBankDocument.feesCharged,
                        transactions = thRecords,
                        pageMetadata = metadata
                ).update(documentType = analyzedBankDocument.getInferredStatementType())

                lastUsedStatement = statements[statementKey]
            } catch (e: Throwable) {
                logger.error(e) { "Exception processing $analyzedBankDocument: $e" }
            }
        }

        return transformNFCUBankStatements(statements)
    }

    /**
     * All the statements for a single joint statement will be under a single statement.
     * We need to split it and remove the combined one. The first transaction for a new statement always says "Beginning Balance"
     */
    fun transformNFCUBankStatements(statements: Map<BankStatementKey, BankStatement>): Collection<BankStatement> {
        statements.filter { it.key.accountNumber?.contains(JointStatementAccountSummary.COMBINED_ACCOUNT_SEPARATOR) == true }.forEach { entry ->
            val accountStatementKeys = entry.key.accountNumber!!.split(JointStatementAccountSummary.COMBINED_ACCOUNT_SEPARATOR).map {
                BankStatementKey(entry.key.date, it, entry.key.classification)
            }

            // we have the beginnings, not the ends, so we can always remove the first and then add the end of the list
            val statementEndings = entry.value.transactions.mapIndexedNotNull { idx, record ->
                if (record.isBeginningBalanceRecord() && idx != 0) idx
                else null
            }.toMutableList()
            statementEndings.add(entry.value.transactions.size)


            // something has gone wrong if these are not equal
//            if (accountStatementKeys.size != statementEndings.size) {
//                return@forEach
//            }

            var lastIdx = 0
            accountStatementKeys.zip(statementEndings).forEach { (accKey, idxOfTransaction) ->
                val actualStatement = statements[accKey]
                val statementTransactions = entry.value.transactions.subList(lastIdx, idxOfTransaction)
                actualStatement?.update(transactions = statementTransactions)
                statementTransactions.map { it.pageMetadata }.distinct().forEach {
                    actualStatement?.update(pageMetadata = it)
                }

                lastIdx = idxOfTransaction + 1
            }
        }

        statements.values.filter { it.isNFCUBank() }.forEach { stmt ->
            stmt.transactions.removeAll { it.isBeginningBalanceRecord() }
        }

        return statements.values.filter { it.accountNumber?.contains(JointStatementAccountSummary.COMBINED_ACCOUNT_SEPARATOR) != true }
    }

    /**
     * Choose the account number to use as the following:
     * 1. if we have the accountNumber on the page, use that
     * 2. check if the last joint account summary matches and if so select the relevant account from the summary
     * 3. use the account number from the last used statement
     */
    fun chooseAccountNumber(statementDate: Date?, analyzedBankDocument: StatementDataModel, lastUsedStatement: BankStatement?, lastJointStatementSummary: JointStatementAccountSummary?) =
        analyzedBankDocument.accountNumber
            ?: lastJointStatementSummary?.findRelevantAccountIfMatches(statementDate, analyzedBankDocument.pageMetadata.classification, analyzedBankDocument.pageNum)
            ?: if (lastUsedStatement?.primaryKey?.couldNotMatch(analyzedBankDocument) != false) null
                else lastUsedStatement.accountNumber


    /**
     * For NFCU bank, The first page does not have the account at all, and the 2nd page could have 2 statements on the same page (for a joint statement).
     * There is always an account table though, and the statementDate appears on every page.
     * So we combine all the accounts into one statement and then split them later.
     *
     * To determine the account:
     * 1. if it's the first page, we pick the first one in the accountSummary table
     * 2. otherwise, if it's the same statement date, just use the last account
     * 3. otherwise, it's a new statement date and there isn't an account summary, which should never happen. Default to the account number from the model?
     */
    fun chooseAccountNumberForNFCUBank(statementDate: Date?, analyzedBankDocument: StatementDataModel, lastUsedStatement: BankStatement?, lastJointStatementSummary: JointStatementAccountSummary?) =
        // should never happen, since lastJointStatementSummary should always be there
        if (lastUsedStatement == null && lastJointStatementSummary == null) {
            analyzedBankDocument.accountNumber
        } else if (statementDate == lastJointStatementSummary!!.statementDate && lastJointStatementSummary.classification == DocumentType.BankTypes.NFCU_BANK) {
            lastJointStatementSummary.getCombinedAccount()
        } else if (statementDate == lastUsedStatement!!.statementDate && lastUsedStatement.classification == DocumentType.BankTypes.NFCU_BANK) {
            lastUsedStatement.accountNumber
        } else {
            analyzedBankDocument.accountNumber
        }

    companion object {
        fun BankStatementKey.couldNotMatch(statementModel: StatementDataModel): Boolean {
            return (statementModel.statementDate != null && this.statementDate != null && statementModel.statementDate != this.statementDate) ||
                    (statementModel.accountNumber != null && this.accountNumber != null && statementModel.accountNumber != this.accountNumber) ||
                    statementModel.pageMetadata.classification != this.classification
        }

        fun TransactionHistoryRecord.isBeginningBalanceRecord() =
            this.description == NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION && this.amount == null

        private const val NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION = "Beginning Balance"
    }
}