package com.goldberg.law.document

import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.StatementDataModel.Keys
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.Statement
import com.goldberg.law.entity.StatementDetails
import com.goldberg.law.entity.TransactionDetails
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.last4Digits
import com.goldberg.law.util.normalizeDate
import com.goldberg.law.verify.BankStatementVerifier
import com.google.inject.Inject
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.*

class DocumentStatementCreator @Inject constructor(private val bankStatementVerifier: BankStatementVerifier) {
    private val logger = KotlinLogging.logger {}

    fun createBankStatements(classification: Classification, model: StatementDataModel): List<Statement> {
        val statementDate = normalizeDate(model.date?.let { modelDate ->
            // for citi credit cards, the date field captures both the start and end
            val dashedParts = modelDate.split("-")
            if (!dashedParts.map { normalizeDate(it) }.contains(null)) modelDate.substringAfter("-").trim()
            else modelDate
        })

        return if (DocumentType.hasMultipleStatements(classification.classificationType)) {
            val records = model.getTransactionRecords()

            val result: MutableList<MutableList<TransactionDetails>> = mutableListOf()
            var currentGroup: MutableList<TransactionDetails>? = null

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
                model.summaryOfAccountsTable!!.records.zip(result) { accountSummary, transactionRecords ->
                    val statementDetails = StatementDetails(
                        statementId = UUID.randomUUID(),
                        date = statementDate,
                        accountNumber = accountSummary.accountNumber?.last4Digits(),
                        beginningBalance = accountSummary.beginningBalance?.asCurrency(),
                        endingBalance = accountSummary.endingBalance?.asCurrency(),
                        interestCharged = model.interestCharged,
                        feesCharged = model.feesCharged,
                        batesStamps = model.getBatesStampsMap(),
                    )
                    val suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(statementDetails, transactionRecords, classification)
                    Statement(classification, statementDetails, suspiciousReasons, transactionRecords)
                }
            } catch (ex: Exception) {
                logger.error { "Exception processing $this: $ex" }
                throw ex
            }
        } else {
            val statementDetails = StatementDetails(
                statementId = UUID.randomUUID(),
                date = statementDate,
                accountNumber = model.accountNumber?.last4Digits(),
                beginningBalance = model.beginningBalance?.asCurrency(),
                endingBalance = model.endingBalance?.asCurrency(),
                interestCharged = model.interestCharged,
                feesCharged = model.feesCharged,
                batesStamps = model.getBatesStampsMap(),
            )
            val transactionRecords = model.getTransactionRecords()
            val suspiciousReasons = bankStatementVerifier.getSuspiciousReasons(statementDetails, transactionRecords, classification)
            listOf(Statement(classification, statementDetails, suspiciousReasons, transactionRecords))
        }
    }

    companion object {
        fun TransactionDetails.isBeginningBalanceRecord() = this.description?.lowercase() in beginningBalanceTransactionDescriptions
        val beginningBalanceTransactionDescriptions = listOf(
            "beginning bal.", "beginning bal", // TFCU
            "beginning balance", // NFCU, TFCU
            "opening balance" // Capital One Joint
        )
    }
}