package com.goldberg.law.document.model.input

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.document.model.input.SummaryOfAccountsTable.Companion.getSummaryOfAccounts
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsCharges.Companion.getTransactionTableCreditsCharges
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal.Companion.getTransactionTableDepositWithdrawal
import com.goldberg.law.document.model.input.tables.TransactionTableAmount.Companion.getTransactionTableAmount
import com.goldberg.law.document.model.input.tables.TransactionTableChecks.Companion.getTransactionTableChecks
import com.goldberg.law.document.model.input.tables.TransactionTableCredits.Companion.getTransactionTableCredits
import com.goldberg.law.document.model.input.tables.TransactionTableDebits.Companion.getTransactionTableDebits
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocumentPage
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.*
import java.math.BigDecimal
import java.util.Date

data class StatementDataModel @JsonCreator constructor(
    @JsonProperty("bankIdentifier") val bankIdentifier: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("pageNum") val pageNum: Int?,
    @JsonProperty("totalPages") val totalPages: Int?,
    @JsonProperty("summaryOfAccountsTable") val summaryOfAccountsTable: SummaryOfAccountsTable?,
    @JsonProperty("transactionTableDepositWithdrawal") val transactionTableDepositWithdrawal: TransactionTableDepositWithdrawal?,
    @JsonProperty("batesStamp") val batesStamp: String?,
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("beginningBalance") val beginningBalance: BigDecimal?,
    @JsonProperty("endingBalance") val endingBalance: BigDecimal?,
    @JsonProperty("transactionTableAmount") val transactionTableAmount: TransactionTableAmount?,
    @JsonProperty("transactionTableCreditsCharges") val transactionTableCreditsCharges: TransactionTableCreditsCharges?,
    @JsonProperty("transactionTableDebits") val transactionTableDebits: TransactionTableDebits?,
    @JsonProperty("transactionTableCredits") val transactionTableCredits: TransactionTableCredits?,
    @JsonProperty("transactionTableChecks") val transactionTableChecks: TransactionTableChecks?,
    @JsonProperty("interestCharged") val interestCharged: BigDecimal?,
    @JsonProperty("feesCharged") val feesCharged: BigDecimal?,
    @JsonProperty("pageMetadata") override val pageMetadata: PdfDocumentPageMetadata
): DocumentDataModel(pageMetadata) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)
    @JsonIgnore
    fun getStatementType(): DocumentType? {
        val isBank = listOfNotNull(
            transactionTableDepositWithdrawal,
            transactionTableCredits,
            transactionTableDebits,
            summaryOfAccountsTable,
            transactionTableChecks
        ).isNotEmpty()
        val isCreditCard = listOfNotNull(transactionTableCreditsCharges, transactionTableAmount).isNotEmpty()
        return when {
            isBank && !isCreditCard -> DocumentType.BANK
            !isBank && isCreditCard -> DocumentType.CREDIT_CARD
            isBank && isCreditCard -> DocumentType.MIXED // this should never happen
            else -> null
        }
    }

    @JsonIgnore
    fun getTransactionRecords(statementDate: Date?, metadata: TransactionHistoryPageMetadata) = listOf(
        transactionTableDepositWithdrawal, transactionTableAmount, transactionTableCreditsCharges,
        transactionTableDebits, transactionTableCredits, transactionTableChecks
    ).flatMap { it?.createHistoryRecords(statementDate, metadata, pageMetadata.documentType) ?: listOf() }

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
        fun AnalyzedDocument.toBankDocument(classifiedPdfDocument: ClassifiedPdfDocumentPage): StatementDataModel = this.fields.let { documentFields ->
            // for citi credit cards, the date field captures both the start and end
            val statementDate = normalizeDate(documentFields[Keys.STATEMENT_DATE]?.valueAsString?.let {
                if (it.contains("-")) it.substringAfter("-").trim()
                else it
            })

            val (page,totalPages) = documentFields[Keys.PAGE_AND_TOTAL]?.valueAsString?.split("/")
                ?.let { if (it.size == 2 && it[0].toIntOrNull() != null && it[1].toIntOrNull() != null) Pair(it[0].toInt(), it[1].toInt()) else null }
                ?: Pair(documentFields[Keys.PAGE_NUM]?.valueAsString?.toInt(), documentFields[Keys.TOTAL_PAGES]?.valueAsString?.toInt())
            StatementDataModel(
                bankIdentifier = documentFields[Keys.BANK_IDENTIFIER]?.valueAsString,
                date = statementDate,
                pageNum = page,
                totalPages = totalPages,
                summaryOfAccountsTable = this.getSummaryOfAccounts(),
                transactionTableDepositWithdrawal = this.getTransactionTableDepositWithdrawal(),
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueAsString,
                batesStamp = documentFields[Keys.BATES_STAMP]?.valueAsString,
                beginningBalance = documentFields[Keys.BEGINNING_BALANCE]?.currencyValue(),
                endingBalance = documentFields[Keys.ENDING_BALANCE]?.currencyValue(),
                feesCharged = documentFields[Keys.FEES_CHARGED]?.positiveCurrencyValue(),
                interestCharged = documentFields[Keys.INTEREST_CHARGED]?.positiveCurrencyValue(),
                transactionTableAmount = this.getTransactionTableAmount(),
                transactionTableCreditsCharges = this.getTransactionTableCreditsCharges(),
                transactionTableCredits = this.getTransactionTableCredits(),
                transactionTableDebits = this.getTransactionTableDebits(),
                transactionTableChecks = this.getTransactionTableChecks(),
                pageMetadata = classifiedPdfDocument.toDocumentMetadata()
            )
        }
        fun blankModel(classifiedPdfDocument: ClassifiedPdfDocumentPage) = StatementDataModel(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            classifiedPdfDocument.toDocumentMetadata()
        )
    }
}