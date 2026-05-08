package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.input.SummaryOfAccountsTable.Companion.getSummaryOfAccounts
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.input.tables.BatesStampTable.Companion.getBatesStampTable
import com.goldberg.law.document.model.input.tables.TransactionTableAmount.Companion.getTransactionTableAmount
import com.goldberg.law.document.model.input.tables.TransactionTableChecks.Companion.getTransactionTableChecks
import com.goldberg.law.document.model.input.tables.TransactionTableCredits.Companion.getTransactionTableCredits
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsCharges.Companion.getTransactionTableCreditsCharges
import com.goldberg.law.document.model.input.tables.TransactionTableDebits.Companion.getTransactionTableDebits
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal.Companion.getTransactionTableDepositWithdrawal
import com.goldberg.law.entity.Classification
import com.goldberg.law.util.*
import java.math.BigDecimal

data class StatementDataModel(
    val documentType: String?,
    val date: String?,
    val accountNumber: String?,
    val beginningBalance: BigDecimal?,
    val endingBalance: BigDecimal?,
    val feesCharged: BigDecimal?,
    val interestCharged: BigDecimal?,
    val summaryOfAccountsTable: SummaryOfAccountsTable?,
    val transactionTableDepositWithdrawal: TransactionTableDepositWithdrawal?,
    val transactionTableAmount: TransactionTableAmount?,
    val transactionTableCreditsCharges: TransactionTableCreditsCharges?,
    val transactionTableDebits: TransactionTableDebits?,
    val transactionTableCredits: TransactionTableCredits?,
    val transactionTableChecks: TransactionTableChecks?,
    val batesStampsTable: BatesStampTable?,
    override val classification: Classification,
): DocumentDataModel(classification) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)

    @JsonIgnore
    fun getTransactionRecords() = listOf(
        transactionTableDepositWithdrawal, transactionTableAmount, transactionTableCreditsCharges,
        transactionTableDebits, transactionTableCredits, transactionTableChecks
    ).flatMap { it?.createTransactionDetails(date, classification) ?: listOf() }

    @JsonIgnore
    fun getBatesStampsMap(): Map<Int, String> = batesStampsTable?.batesStamps?.associate {
            classification.pagesOrdered[it.page - 1] to it.`val`
        } ?: mapOf()

    object Keys {
        const val ACCOUNT_NUMBER = "AccountNumber"
        const val STATEMENT_DATE = "StatementDate"
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

        const val BATES_STAMPS = "BatesStamps"
    }



    companion object {
        fun AnalyzedDocument.toBankDocument(classification: Classification): StatementDataModel = this.fields.let { documentFields ->
            StatementDataModel(
                documentType = this.documentType,
                date = documentFields[Keys.STATEMENT_DATE]?.valueString,
                summaryOfAccountsTable = this.getSummaryOfAccounts(),
                transactionTableDepositWithdrawal = this.getTransactionTableDepositWithdrawal(),
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueString,
                beginningBalance = documentFields[Keys.BEGINNING_BALANCE]?.currencyValue(),
                endingBalance = documentFields[Keys.ENDING_BALANCE]?.currencyValue(),
                feesCharged = documentFields[Keys.FEES_CHARGED]?.positiveCurrencyValue(),
                interestCharged = documentFields[Keys.INTEREST_CHARGED]?.positiveCurrencyValue(),
                transactionTableAmount = this.getTransactionTableAmount(),
                transactionTableCreditsCharges = this.getTransactionTableCreditsCharges(),
                transactionTableCredits = this.getTransactionTableCredits(),
                transactionTableDebits = this.getTransactionTableDebits(),
                transactionTableChecks = this.getTransactionTableChecks(),
                batesStampsTable = this.getBatesStampTable(),
                classification = classification,
            )
        }
        fun blankModel(classification: Classification) = StatementDataModel(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            classification
        )
    }
}