package com.goldberg.law.document.model.output

import com.goldberg.law.pdf.model.DocumentType
import com.goldberg.law.util.*
import com.goldberg.law.document.model.input.StatementDataModel.Keys as FieldKeys
import io.github.oshai.kotlinlogging.KotlinLogging
import java.util.Calendar
import java.util.Date

data class BankStatement(
    val filename: String,
    val classification: String,
    val statementDate: Date?,
    var accountNumber: String? = null,
    var bankIdentifier: String? = null,
    var startPage: Int = 1, // for combined statements
    var totalPages: Int? = null,
    var beginningBalance: Double? = null,
    var endingBalance: Double? = null,
    // sometimes interest and fees are included in transactions, other times not, so we add them here
    var interestCharged: Double? = null,
    var feesCharged: Double? = null,
    val transactions: MutableList<TransactionHistoryRecord> = mutableListOf()
) {
    var statementType: DocumentType? = DocumentType.getBankType(classification)
    private val logger = KotlinLogging.logger {}
    val primaryKey = BankStatementKey(statementDate, accountNumber)
    constructor(filename: String, classification: String, key: BankStatementKey, startPage: Int? = 1) : this(filename, classification, key.statementDate, key.accountNumber, startPage = startPage ?: 1)

    fun update(documentType: DocumentType? = null,
               accountNumber: String? = null,
               bankIdentifier: String? = null,
               totalPages: Int? = null,
               beginningBalance: Double? = null,
               endingBalance: Double? = null,
               interestCharged: Double? = null,
               feesCharged: Double? = null,
               transactions: List<TransactionHistoryRecord>? = null): BankStatement {
        this.statementType = chooseNewValue(this.statementType, documentType, "Statement Type", DocumentType.MIXED)
        this.accountNumber = chooseNewValue(this.accountNumber, accountNumber, FieldKeys.ACCOUNT_NUMBER)
        this.bankIdentifier = chooseNewValue(this.bankIdentifier, bankIdentifier, FieldKeys.BANK_IDENTIFIER)
        this.totalPages = chooseNewValue(this.totalPages, totalPages, FieldKeys.TOTAL_PAGES)
        this.beginningBalance = chooseNewValue(this.beginningBalance, beginningBalance, FieldKeys.BEGINNING_BALANCE)
        this.endingBalance = chooseNewValue(this.endingBalance, endingBalance, FieldKeys.ENDING_BALANCE)
        this.interestCharged = chooseNewValue(this.interestCharged, interestCharged, FieldKeys.INTEREST_CHARGED)
        this.feesCharged = chooseNewValue(this.feesCharged, feesCharged, FieldKeys.FEES_CHARGED)

        if (transactions != null) this.transactions.addAll(transactions)
        return this
    }

    private fun <T> chooseNewValue(original: T?, new: T?, fieldName: String, valueIfDifferent: T? = null): T? =
        if (original != null && new != null && original != new) {
            valueIfDifferent ?: new.also { otherSuspiciousReasons.add(SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(fieldName, original.toString(), new.toString())) }
        } else new ?: original

    fun getNetTransactions(): Double = try {
        transactions
            .map { it.amount ?: 0.0 }
            .reduce {acc, amt -> (acc + amt).roundToTwoDecimalPlaces() }.roundToTwoDecimalPlaces()
    } catch (e: UnsupportedOperationException) {
        0.0.also { logger.error(e) { "Unable to calculate net deposits for statement $primaryKey (probably means no transactions found): $e" } }
    }

    fun toCsv(): String = transactions.sortedBy { it.date }.joinToString("\n") {
        it.toCsv(accountNumber, classification)
    }

    fun toStatementSummary() = StatementSummaryEntry(
        accountNumber,
        classification,
        beginningBalance,
        endingBalance,
        getNetTransactions(),
        transactions.size,
        transactions.mapNotNull { it.pageMetadata.batesStamp }.toSet(),
        filename,
        isSuspicious(),
        getSuspiciousReasons()
    )

    /**
     * CALCULATING SUSPICIOUS REASONS
     */
    private val otherSuspiciousReasons = mutableListOf<String>()

    fun isSuspicious(): Boolean {
        return getSuspiciousReasons().isNotEmpty()
    }
    fun getSuspiciousReasons(): List<String> = suspiciousReasonMap.mapNotNull { if (it.first(this)) it.second(this) else null }
        .plus(transactions.flatMap { it.getSuspiciousReasons() })
        .plus(otherSuspiciousReasons)

    private fun getMissingFields() = listOf(
        Pair(FieldKeys.STATEMENT_DATE, statementDate),
        Pair(FieldKeys.ACCOUNT_NUMBER, accountNumber),
        Pair(FieldKeys.BEGINNING_BALANCE, beginningBalance),
        Pair(FieldKeys.ENDING_BALANCE, endingBalance)
    ).filter { it.second == null }.map { it.first }

    private fun hasMissingFields(): Boolean = getMissingFields().isNotEmpty()

    fun hasSuspiciousRecords(): Boolean = transactions.find { it.isSuspicious() } != null

    fun getTransactionDatesOutsideOfStatement(): List<String> {
        if (statementDate == null) return listOf()
        val twoMonthsAgo = Calendar.getInstance().apply { time = statementDate; add(Calendar.MONTH, -2) }.time
        return transactions.filter { it.date != null && (it.date.after(statementDate) || it.date.before(twoMonthsAgo)) }
            .map { it.date!!.toTransactionDate() }
    }

    fun hasRecordsWithIncorrectDates() = getTransactionDatesOutsideOfStatement().isNotEmpty()

    private fun numbersAddUpBank(): Boolean = beginningBalance?.plusSafe(getNetTransactions()) == endingBalance
    private fun numbersAddUpCreditCard(): Boolean = beginningBalance?.minusSafe(getNetTransactions()) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plusSafe(interestCharged ?: 0.0) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plusSafe(feesCharged ?: 0.0) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plusSafe(interestCharged ?: 0.0)?.plus(feesCharged ?: 0.0) == endingBalance

    // for credit card statements, all the numbers become negative, so we test the absolute value (TODO: only do this for CC)
    // we also add in interest, fees, or both to see if they add up, because some statements include it in transactions and others don't
    private fun numbersAddUp(): Boolean {
        return when (statementType) {
            DocumentType.BANK -> numbersAddUpBank()
            DocumentType.CREDIT_CARD -> numbersAddUpCreditCard()
            else -> numbersAddUpBank() || numbersAddUpCreditCard()
        }
    }

    fun numbersDoNotAddUp(): Boolean = !numbersAddUp()
    private fun hasNoRecords(): Boolean = transactions.isEmpty()

    // TODO: fix suspicious reasons
    object SuspiciousReasons {
        const val MISSING_FIELDS = "Missing fields: %s"
        const val BALANCE_DOES_NOT_ADD_UP = "Beginning balance (%f) + net deposits (%f) != ending balance (%f). Expected (%f)"
        const val NO_TRANSACTIONS_FOUND = "No transactions recorded"
        const val CONTAINS_SUSPICIOUS_RECORDS = "Contains suspicious records"
        const val MULTIPLE_FIELD_VALUES = "Found multiple values for [%s]: [%s, %s]"
        const val INCORRECT_DATES = "Found transactions with dates outside of this statement: %s"
    }

    companion object {
        private val suspiciousReasonMap: List<Pair<(BankStatement) -> Boolean, (BankStatement) -> String>> = listOf(
            Pair(BankStatement::hasMissingFields) { stmt -> SuspiciousReasons.MISSING_FIELDS.format(stmt.getMissingFields().toString()) },
            Pair(BankStatement::numbersDoNotAddUp) { stmt->
                val transactions = if (stmt.statementType == DocumentType.CREDIT_CARD) 0 - stmt.getNetTransactions() else stmt.getNetTransactions()
                val expected = if (stmt.beginningBalance == null || stmt.endingBalance == null) null
                    else if (stmt.statementType == DocumentType.CREDIT_CARD) stmt.beginningBalance!! - stmt.endingBalance!!
                    else stmt.endingBalance!! - stmt.beginningBalance!!
                SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(stmt.beginningBalance, transactions, stmt.endingBalance, expected)
           },
            Pair(BankStatement::hasNoRecords) { stmt-> SuspiciousReasons.NO_TRANSACTIONS_FOUND },
            Pair(BankStatement::hasSuspiciousRecords) { stmt-> SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS },
            Pair(BankStatement::hasRecordsWithIncorrectDates) { stmt -> SuspiciousReasons.INCORRECT_DATES.format(stmt.getTransactionDatesOutsideOfStatement()) }
        )
    }
}