package com.goldberg.law.document.model.input

import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.ManualRecordTable
import com.goldberg.law.document.model.input.SummaryOfAccountsTable.Companion.getSummaryOfAccounts
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.input.tables.BatesStampTable.Companion.getBatesStampTable
import com.goldberg.law.document.model.input.tables.TransactionTableCreditsCharges.Companion.getTransactionTableCreditsCharges
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal.Companion.getTransactionTableDepositWithdrawal
import com.goldberg.law.document.model.input.tables.TransactionTableAmount.Companion.getTransactionTableAmount
import com.goldberg.law.document.model.input.tables.TransactionTableChecks.Companion.getTransactionTableChecks
import com.goldberg.law.document.model.input.tables.TransactionTableCredits.Companion.getTransactionTableCredits
import com.goldberg.law.document.model.input.tables.TransactionTableDebits.Companion.getTransactionTableDebits
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.*
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal

private val logger = KotlinLogging.logger {}

data class StatementDataModel @JsonCreator constructor(
    @JsonProperty("documentType") val documentType: String?,
    @JsonProperty("date") val date: String?,
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("beginningBalance") val beginningBalance: BigDecimal?,
    @JsonProperty("endingBalance") val endingBalance: BigDecimal?,
    @JsonProperty("feesCharged") val feesCharged: BigDecimal?,
    @JsonProperty("interestCharged") val interestCharged: BigDecimal?,
    @JsonProperty("summaryOfAccountsTable") val summaryOfAccountsTable: SummaryOfAccountsTable?,
    @JsonProperty("transactionTableDepositWithdrawal") val transactionTableDepositWithdrawal: TransactionTableDepositWithdrawal?,
    @JsonProperty("transactionTableAmount") val transactionTableAmount: TransactionTableAmount?,
    @JsonProperty("transactionTableCreditsCharges") val transactionTableCreditsCharges: TransactionTableCreditsCharges?,
    @JsonProperty("transactionTableDebits") val transactionTableDebits: TransactionTableDebits?,
    @JsonProperty("transactionTableCredits") val transactionTableCredits: TransactionTableCredits?,
    @JsonProperty("transactionTableChecks") val transactionTableChecks: TransactionTableChecks?,
    @JsonProperty("batesStamps") val batesStampsTable: BatesStampTable?,
    @JsonProperty("pageMetadata") override val pageMetadata: ClassifiedPdfMetadata,
    @JsonProperty("manualRecordsTable") val manualRecordTable: ManualRecordTable? = null
): DocumentDataModel(pageMetadata) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)

    constructor(documentType: String?, date: String, accountNumber: String,
                beginningBalance: BigDecimal, endingBalance: BigDecimal, interestCharged: BigDecimal?, feesCharged: BigDecimal?,
                batesStamps: BatesStampTable?, pageMetadata: ClassifiedPdfMetadata, manualRecordTable: ManualRecordTable?
    ): this(documentType = documentType, date = date,
        accountNumber = accountNumber, beginningBalance = beginningBalance, endingBalance = endingBalance,
        interestCharged = interestCharged, feesCharged = feesCharged, batesStampsTable = batesStamps,
        pageMetadata = pageMetadata, manualRecordTable = manualRecordTable,
        summaryOfAccountsTable = null, transactionTableCreditsCharges = null, transactionTableChecks = null, transactionTableDebits = null,
        transactionTableDepositWithdrawal = null, transactionTableAmount = null, transactionTableCredits = null,
    )

    @JsonIgnore
    fun getTransactionRecords() = listOf(
        transactionTableDepositWithdrawal, transactionTableAmount, transactionTableCreditsCharges,
        transactionTableDebits, transactionTableCredits, transactionTableChecks, manualRecordTable
    ).flatMap { it?.createHistoryRecords(date, pageMetadata) ?: listOf() }

    @JsonIgnore
    fun getBatesStampsMap(): Map<Int, String> = if (isManuallyOverriden()) {
        // when manually overridden the page is directly in the bates stamp
        batesStampsTable?.batesStamps?.associate {
            it.page to it.`val`
        } ?: mapOf()
    } else {
        batesStampsTable?.batesStamps?.associate {
            pageMetadata.pagesOrdered[it.page - 1] to it.`val`
        } ?: mapOf()
    }

    @JsonIgnore
    fun isManuallyOverriden() = this.manualRecordTable != null

    fun toBankStatement(): List<BankStatement> = if (DocumentType.hasMultipleStatements(pageMetadata.classification) && !isManuallyOverriden()) {
        val records = getTransactionRecords()

        val result: MutableList<MutableList<TransactionHistoryRecord>> = mutableListOf()
        var currentGroup: MutableList<TransactionHistoryRecord>? = null

        for (record in records) {
            if (record.isBeginningBalanceRecord()) {
                if (currentGroup != null) {
                    result.add(currentGroup)
                }
                currentGroup = mutableListOf()
            } else {
                if (currentGroup == null) currentGroup = mutableListOf()
                currentGroup.add(record)
            }
        }

        if (currentGroup != null) result.add(currentGroup)
        try {
            summaryOfAccountsTable!!.records.zip(result) { accountSummary, transactionRecords ->
                BankStatement(
                    pageMetadata = pageMetadata,
                    date = date,
                    accountNumber = accountSummary.accountNumber,
                    beginningBalance = accountSummary.beginningBalance?.stripTrailingZeros(),
                    endingBalance = accountSummary.endingBalance?.stripTrailingZeros(),
                    interestCharged = interestCharged,
                    feesCharged = feesCharged,
                    transactions = transactionRecords,
                    batesStamps = getBatesStampsMap(),
                )
            }
        } catch (ex: Exception) {
            logger.error { "Exception processing $this: $ex" }
            listOf(BankStatement(
                pageMetadata = pageMetadata,
                date = null,
                batesStamps = getBatesStampsMap(),
                transactions = records
            ))
        }
    } else {
        listOf(BankStatement(
            pageMetadata = pageMetadata,
            date = date,
            accountNumber = accountNumber,
            beginningBalance = beginningBalance?.stripTrailingZeros(),
            endingBalance = endingBalance?.stripTrailingZeros(),
            interestCharged = interestCharged,
            feesCharged = feesCharged,
            transactions = getTransactionRecords(),
            batesStamps = getBatesStampsMap(),
        ))
    }

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
        fun AnalyzedDocument.toBankDocument(classifiedPdfDocument: ClassifiedPdfDocument): StatementDataModel = this.fields.let { documentFields ->
            // for citi credit cards, the date field captures both the start and end
            val statementDate = normalizeDate(documentFields[Keys.STATEMENT_DATE]?.valueString?.let {
                if (it.contains("-")) it.substringAfter("-").trim()
                else it
            })

            StatementDataModel(
                documentType = this.documentType,
                date = statementDate,
                summaryOfAccountsTable = this.getSummaryOfAccounts(),
                transactionTableDepositWithdrawal = this.getTransactionTableDepositWithdrawal(),
                accountNumber = documentFields[Keys.ACCOUNT_NUMBER]?.valueString?.last4Digits(),
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
                pageMetadata = classifiedPdfDocument.toDocumentMetadata(),
            )
        }
        fun blankModel(classifiedPdfDocument: ClassifiedPdfDocument) = StatementDataModel(
            null, null, null, null, null, null, null, null, null, null, null, null, null, null, null,
            classifiedPdfDocument.toDocumentMetadata()
        )

        fun TransactionHistoryRecord.isBeginningBalanceRecord() = (this.description in beginningBalanceTransactionDescriptions) && this.amount == null
        
        const val NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION = "Beginning Balance"
        const val CAPITAL_ONE_JOINT_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION = "Opening Balance"
        val beginningBalanceTransactionDescriptions = listOf(NFCU_BANK_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION, CAPITAL_ONE_JOINT_BEGINNING_BALANCE_TRANSACTION_DESCRIPTION)
    }
}