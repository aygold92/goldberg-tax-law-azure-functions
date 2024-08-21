package com.goldberg.law.document.model.output

import com.goldberg.law.pdf.model.StatementType
import com.goldberg.law.util.roundToTwoDecimalPlaces
import com.goldberg.law.document.model.input.StatementDataModel.Keys as FieldKeys
import io.github.oshai.kotlinlogging.KotlinLogging
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
    var statementType: StatementType? = null,
    val thPages: MutableList<TransactionHistoryPage> = mutableListOf()
) {
    private val logger = KotlinLogging.logger {}
    val primaryKey = BankStatementKey(statementDate, accountNumber)
    constructor(filename: String, classification: String, key: BankStatementKey, startPage: Int? = 1) : this(filename, classification, key.statementDate, key.accountNumber, startPage = startPage ?: 1)

    fun update(statementType: StatementType? = null,
               accountNumber: String? = null,
               bankIdentifier: String? = null,
               totalPages: Int? = null,
               beginningBalance: Double? = null,
               endingBalance: Double? = null,
               interestCharged: Double? = null,
               feesCharged: Double? = null,
               thPage: TransactionHistoryPage? = null): BankStatement {
        this.statementType = chooseNewValue(this.statementType, statementType, "Statement Type")
        this.accountNumber = chooseNewValue(this.accountNumber, accountNumber, FieldKeys.ACCOUNT_NUMBER)
        this.bankIdentifier = chooseNewValue(this.bankIdentifier, bankIdentifier, FieldKeys.BANK_IDENTIFIER)
        this.totalPages = chooseNewValue(this.totalPages, totalPages, FieldKeys.TOTAL_PAGES)
        this.beginningBalance = chooseNewValue(this.beginningBalance, beginningBalance, FieldKeys.BEGINNING_BALANCE)
        this.endingBalance = chooseNewValue(this.endingBalance, endingBalance, FieldKeys.ENDING_BALANCE)
        this.interestCharged = chooseNewValue(this.interestCharged, interestCharged, FieldKeys.INTEREST_CHARGED)
        this.feesCharged = chooseNewValue(this.feesCharged, feesCharged, FieldKeys.FEES_CHARGED)

        if (thPage != null) thPages.add(thPage)
        return this
    }

    private fun <T> chooseNewValue(original: T?, new: T?, fieldName: String): T? =
        if (original != null && new != null && original != new) {
            new.also { otherSuspiciousReasons.add(SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(fieldName, original.toString(), new.toString())) }
        } else new ?: original

    fun getNetTransactions(): Double = try {
        thPages.flatMap { it.transactionHistoryRecords }
            .map { it.amount ?: 0.0 }
            .reduce {acc, amt -> (acc + amt).roundToTwoDecimalPlaces() }
    } catch (e: UnsupportedOperationException) {
        0.0.also { logger.error(e) { "Unable to calculate net deposits for statement $primaryKey (probably means no transactions found): $e" } }
    }

    fun toCsv(): String = thPages.sortedBy { it.statementPageNum }.joinToString("\n") {
        it.toCsv(accountNumber)
    }

    /**
     * CALCULATING SUSPICIOUS REASONS
     */
    private val otherSuspiciousReasons = mutableListOf<String>()

    private val suspiciousReasonMap: List<Pair<() -> Boolean, () -> String>> = listOf(
        Pair(::hasMissingFields) { SuspiciousReasons.MISSING_FIELDS.format(getMissingFields().toString()) },
        Pair(::numbersDoNotAddUp) { SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(beginningBalance, getNetTransactions(), endingBalance) },
        Pair(::hasNoRecords) { SuspiciousReasons.NO_TRANSACTIONS_FOUND },
        Pair(::hasSuspiciousPages) { SuspiciousReasons.CONTAINS_SUSPICIOUS_PAGES }
    )

    fun isSuspicious(): Boolean {
        return getSuspiciousReasons().isNotEmpty()
    }
    fun getSuspiciousReasons(): List<String> = suspiciousReasonMap.mapNotNull { if (it.first()) it.second() else null }
        .plus(thPages.flatMap { it.getSuspiciousReasons() })
        .plus(otherSuspiciousReasons)

    private fun getMissingFields() = listOf(
        Pair(FieldKeys.STATEMENT_DATE, statementDate),
        Pair(FieldKeys.ACCOUNT_NUMBER, accountNumber),
        Pair(FieldKeys.BEGINNING_BALANCE, beginningBalance),
        Pair(FieldKeys.ENDING_BALANCE, endingBalance)
    ).filter { it.second == null }.map { it.first }

    private fun hasMissingFields(): Boolean = getMissingFields().isNotEmpty()

    fun hasSuspiciousPages(): Boolean = thPages.find { it.isSuspicious() } != null

    private fun numbersAddUpBank(): Boolean = beginningBalance?.plus(getNetTransactions()) == endingBalance
    private fun numbersAddUpCreditCard(): Boolean = beginningBalance?.minus(getNetTransactions()) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(interestCharged ?: 0.0) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(feesCharged ?: 0.0) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(interestCharged ?: 0.0)?.plus(feesCharged ?: 0.0) == endingBalance

    // for credit card statements, all the numbers become negative, so we test the absolute value (TODO: only do this for CC)
    // we also add in interest, fees, or both to see if they add up, because some statements include it in transactions and others don't
    private fun numbersAddUp(): Boolean {
        return when (statementType) {
            StatementType.BANK -> numbersAddUpBank()
            StatementType.CREDIT_CARD -> numbersAddUpCreditCard()
            else -> numbersAddUpBank() || numbersAddUpCreditCard()
        }

    }

    fun numbersDoNotAddUp(): Boolean = !numbersAddUp()
    private fun hasNoRecords(): Boolean = thPages.flatMap { it.transactionHistoryRecords }.isEmpty()

    object SuspiciousReasons {
        const val MISSING_FIELDS = "Missing fields: %s"
        const val BALANCE_DOES_NOT_ADD_UP = "Beginning balance (%f) + net deposits (%f) != ending balance (%f)"
        const val NO_TRANSACTIONS_FOUND = "No transactions recorded"
        const val CONTAINS_SUSPICIOUS_PAGES = "Contains suspicious pages"
        const val MULTIPLE_FIELD_VALUES = "Found multiple values for [%s]: [%s, %s]"
    }
}