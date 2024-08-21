package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.goldberg.law.document.model.input.SummaryOfAccounts.Companion.getSummaryOfAccounts
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsCharges.Companion.getTransactionTableCreditsCharges
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal.Companion.getTransactionTableDepositWithdrawal
import com.goldberg.law.document.model.input.tables.TransactionTableAmount.Companion.getTransactionTableAmount
import com.goldberg.law.document.model.input.tables.TransactionTableChecks.Companion.getTransactionTableChecks
import com.goldberg.law.document.model.input.tables.TransactionTableCredits.Companion.getTransactionTableCredits
import com.goldberg.law.document.model.input.tables.TransactionTableDebits.Companion.getTransactionTableDebits
import com.goldberg.law.pdf.model.StatementType
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.roundToTwoDecimalPlaces
import java.util.Date

class StatementDataModel(
    val bankIdentifier: String?,
    val statementDate: Date?,
    val pageNum: Int?,
    val totalPages: Int?,
    val summaryOfAccounts: SummaryOfAccounts?,
    val transactionTableDepositWithdrawal: TransactionTableDepositWithdrawal?,
    val batesStamp: String?,
    val accountNumber: String?,
    val beginningBalance: Double?,
    val endingBalance: Double?,
    val transactionTableAmount: TransactionTableAmount?,
    val transactionTableCreditsCharges: TransactionTableCreditsCharges?,
    val transactionTableDebits: TransactionTableDebits?,
    val transactionTableCredits: TransactionTableCredits?,
    val transactionTableChecks: TransactionTableChecks?,
    val interestCharged: Double?,
    val feesCharged: Double?
) {
    fun getStatementType(): StatementType? {
        val isBank = listOfNotNull(
            transactionTableDepositWithdrawal,
            transactionTableCredits,
            transactionTableDebits,
            summaryOfAccounts,
            transactionTableChecks
        ).isNotEmpty()
        val isCreditCard = listOfNotNull(transactionTableCreditsCharges, transactionTableAmount).isNotEmpty()
        return when {
            isBank && !isCreditCard -> StatementType.BANK
            !isBank && isCreditCard -> StatementType.CREDIT_CARD
            isBank && isCreditCard -> StatementType.MIXED // this should never happen
            else -> null
        }
    }
    fun getTransactionRecords(statementYear: String?) = listOf(
        transactionTableDepositWithdrawal, transactionTableAmount, transactionTableCreditsCharges,
        transactionTableDebits, transactionTableCredits, transactionTableChecks
    ).flatMap { it?.getHistoryRecords(statementYear) ?: listOf() }

    object Keys {
        const val BANK_IDENTIFIER = "BankIdentifier"
        const val ACCOUNT_NUMBER = "AccountNumber"
        const val STATEMENT_DATE = "StatementDate"
        const val PAGE_NUM = "Page"
        const val TOTAL_PAGES = "TotalPages"
        const val PAGE_AND_TOTAL = "Page/Total" // for Amex, it shows up as one value like "2/6"
        const val BATES_STAMP = "BatesStamp"
        const val BEGINNING_BALANCE = "BeginningBalance"
        const val ENDING_BALANCE = "EndingBalance"
        const val INTEREST_CHARGED = "InterestCharged"
        const val FEES_CHARGED = "FeesCharged"
        // for WF bank
        const val TRANSACTION_TABLE_DEPOSIT_WITHDRAWAL = "TransactionTableDepositWithdrawal"
        const val ACCOUNT_SUMMARY_TABLE = "SummaryOfAccounts"
        // for Eagle Bank
        const val TRANSACTION_TABLE_CREDITS = "TransactionTableCredits"
        const val TRANSACTION_TABLE_DEBITS = "TransactionTableDebits"
        const val TRANSACTION_TABLE_CHECKS = "ChecksTable"
        // for most credit cards
        const val TRANSACTION_TABLE_AMOUNT = "TransactionTableAmount"
        // for WF credit card
        const val TRANSACTION_TABLE_CREDITS_CHARGES = "TransactionTableCreditsCharges"
    }



    companion object {
        fun AnalyzedDocument.toBankDocument(): StatementDataModel = this.fields.let { documentFields ->
            // for citi credit cards, the date field captures both the start and end
            val statementDate = fromWrittenDate(documentFields[Keys.STATEMENT_DATE]?.valueAsString?.let {
                if (it.contains("-")) it.substringAfter("-").trim()
                else it
            })

            val (page,totalPages) = documentFields[Keys.PAGE_AND_TOTAL]?.valueAsString?.split("/")
                ?.let { Pair(it[0].toInt(), it[1].toInt()) }
                ?: Pair(documentFields[Keys.PAGE_NUM]?.valueAsString?.toInt(), documentFields[Keys.TOTAL_PAGES]?.valueAsString?.toInt())
            StatementDataModel(
                bankIdentifier = documentFields[Keys.BANK_IDENTIFIER]?.valueAsString,
                statementDate = statementDate,
                pageNum = page,
                totalPages = totalPages,
                summaryOfAccounts = this.getSummaryOfAccounts(),
                transactionTableDepositWithdrawal = this.getTransactionTableDepositWithdrawal(),
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueAsString,
                batesStamp = documentFields[Keys.BATES_STAMP]?.valueAsString,
                beginningBalance = documentFields[Keys.BEGINNING_BALANCE]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                endingBalance = documentFields[Keys.ENDING_BALANCE]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                feesCharged = documentFields[Keys.FEES_CHARGED]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                interestCharged = documentFields[Keys.INTEREST_CHARGED]?.valueAsDouble?.roundToTwoDecimalPlaces(),
                transactionTableAmount = this.getTransactionTableAmount(),
                transactionTableCreditsCharges = this.getTransactionTableCreditsCharges(),
                transactionTableCredits = this.getTransactionTableCredits(),
                transactionTableDebits = this.getTransactionTableDebits(),
                transactionTableChecks = this.getTransactionTableChecks(),
            )
        }
    }
}