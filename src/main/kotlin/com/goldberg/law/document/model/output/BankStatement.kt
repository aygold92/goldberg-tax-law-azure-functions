package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.ZERO
import com.goldberg.law.util.addQuotes
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*
import com.goldberg.law.document.model.input.StatementDataModel.Keys as FieldKeys

@JsonIgnoreProperties(ignoreUnknown = true)
data class BankStatement @JsonCreator constructor(
    @JsonProperty("filename")
    val filename: String,
    @JsonProperty("classification")
    val classification: String,
    @JsonProperty("date")
    val date: String?,
    @JsonProperty("accountNumber")
    var accountNumber: String? = null,
    @JsonProperty("bankIdentifier")
    var bankIdentifier: String? = null,
    @JsonProperty("startPage")
    var startPage: Int = 1, // for combined statements
    @JsonProperty("totalPages")
    var totalPages: Int? = null,
    @JsonProperty("beginningBalance")
    var beginningBalance: BigDecimal? = null,
    @JsonProperty("endingBalance")
    var endingBalance: BigDecimal? = null,
    // sometimes interest and fees are included in transactions, other times not, so we include them here
    @JsonProperty("interestCharged")
    var interestCharged: BigDecimal? = null,
    @JsonProperty("feesCharged")
    var feesCharged: BigDecimal? = null,
    @JsonProperty("transactions")
    val transactions: MutableList<TransactionHistoryRecord> = mutableListOf(),
    @JsonProperty("pages")
    val pages: MutableSet<TransactionHistoryPageMetadata> = mutableSetOf(),
) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)
    @JsonIgnore @Transient
    var statementType: DocumentType? = DocumentType.getBankType(classification)
    @JsonIgnore @Transient
    private val logger = KotlinLogging.logger {}
    @JsonIgnore @Transient
    val primaryKey = BankStatementKey(date, accountNumber, classification)

    constructor(filename: String, classification: String, key: BankStatementKey, startPage: Int? = 1) :
            this(filename, classification, key.date, key.accountNumber, startPage = startPage ?: 1)

    @JsonIgnore
    fun fileName() = getFileName(accountNumber, date, classification, filename, pages.map { it.pageData })

    fun update(documentType: DocumentType? = null,
               accountNumber: String? = null,
               bankIdentifier: String? = null,
               totalPages: Int? = null,
               beginningBalance: BigDecimal? = null,
               endingBalance: BigDecimal? = null,
               interestCharged: BigDecimal? = null,
               feesCharged: BigDecimal? = null,
               transactions: List<TransactionHistoryRecord>? = null,
               pageMetadata: TransactionHistoryPageMetadata? = null
    ): BankStatement {
        this.statementType = chooseNewValue(this.statementType, documentType, "Statement Type", DocumentType.MIXED)
        this.accountNumber = chooseNewValue(this.accountNumber, accountNumber, FieldKeys.ACCOUNT_NUMBER)
        this.bankIdentifier = chooseNewValue(this.bankIdentifier, bankIdentifier, FieldKeys.BANK_IDENTIFIER)
        this.totalPages = chooseNewValue(this.totalPages, totalPages, FieldKeys.TOTAL_PAGES)
        this.beginningBalance = chooseNewValue(this.beginningBalance, beginningBalance, FieldKeys.BEGINNING_BALANCE)
        this.endingBalance = chooseNewValue(this.endingBalance, endingBalance, FieldKeys.ENDING_BALANCE)
        this.interestCharged = chooseNewValue(this.interestCharged, interestCharged, FieldKeys.INTEREST_CHARGED)
        this.feesCharged = chooseNewValue(this.feesCharged, feesCharged, FieldKeys.FEES_CHARGED)

        if (transactions != null) this.transactions.addAll(transactions)
        if (pageMetadata != null) this.pages.add(pageMetadata)
        return this
    }

    private fun <T> chooseNewValue(original: T?, new: T?, fieldName: String, valueIfDifferent: T? = null): T? =
        if (original != null && new != null && original != new) {
            valueIfDifferent ?: new.also { otherSuspiciousReasons.add(SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(fieldName, original.toString(), new.toString())) }
        } else new ?: original

    @JsonIgnore
    fun getNetTransactions(): BigDecimal = try {
        if (transactions.isEmpty()) {
            0.asCurrency().also { logger.error { "Unable to calculate net deposits for statement $primaryKey: statement no transactions" } }
        } else {
            transactions
                .map { it.amount ?: 0.asCurrency() }
                .reduce {acc, amt -> (acc + amt) }
        }
    } catch (e: UnsupportedOperationException) {
        0.asCurrency().also { logger.error(e) { "Unable to calculate net deposits for statement $primaryKey: $e" } }
    }

    // TODO
    fun addInterestAndFeesTransactionsIfNecessary() {
        /*if (beginningBalance == null || endingBalance == null || (interestCharged?.isZero() == false && feesCharged?.isZero() == false) ||
            beginningBalance?.minus(getNetTransactions()) == endingBalance ||
            beginningBalance?.minusSafe(getNetTransactions())?.plusSafe(interestCharged ?: 0.0) == endingBalance)
            return

        val interestChargedRecord = TransactionHistoryRecord(date = statementDate, description = "Interest Charged", amount = interestCharged, pageMetadata = transactions.first().pageMetadata)
        val feesChargedRecord = TransactionHistoryRecord(date = statementDate, description = "Fees Charged", amount = interestCharged, pageMetadata = transactions.first().pageMetadata)
        if (beginningBalance?.minusSafe(getNetTransactions())?.plusSafe(interestCharged ?: 0.0) == endingBalance) {

        }
        if (beginningBalance?.minusSafe(getNetTransactions())?.plusSafe(feesCharged ?: 0.0) == endingBalance) {


        }
        if (beginningBalance?.minusSafe(getNetTransactions())?.plusSafe(interestCharged ?: 0.0)?.plus(feesCharged ?: 0.0) == endingBalance) {

        }*/
    }

    @JsonIgnore
    fun getMissingChecks(): List<CheckDataKey> = transactions.filter { it.checkNumber != null && it.checkDataModel == null }
        .map { CheckDataKey(accountNumber, it.checkNumber) }

    @JsonIgnore
    fun getCheckKeysUsed(): List<CheckDataKey> = transactions.mapNotNull { it.checkDataModel?.checkDataKey }
    @JsonIgnore
    fun getCheckModelsUsed(): List<CheckDataModel> = transactions.mapNotNull { it.checkDataModel }

    fun toStatementSummary() = StatementSummaryEntry(
        accountNumber,
        classification,
        statementDate,
        beginningBalance,
        endingBalance,
        getNetTransactions(),
        transactions.size,
        pages.mapNotNull { it.batesStamp }.toSet(),
        filename,
        pages.map { it.filePageNumber }.toSet(),
        isSuspicious(),
        getSuspiciousReasons()
    )

    fun toCsv(): String = transactions.sortedBy { it.date }.joinToString("\n") {
        it.toCsv(accountNumber, classification)
    }

    fun suspiciousReasonsToCsv(): String {
        // add suspicious reasons, and include the account and statementDate
        // NOTE: be very careful as this must line up with the headers defined in TransactionHistoryRecord
        val suspiciousReasons = getSuspiciousReasons()
        return listOf(
                if (suspiciousReasons.isNotEmpty()) suspiciousReasons.toString().addQuotes() else "Verified",
                "",
                "",
                "",
                TransactionHistoryPageMetadata.joinAccountNumber(accountNumber, classification),
                "",
                date
            ).joinToString(",")

    }

    fun sumOfTransactionsToCsv(startLine: Int): String {
        val endLine = startLine + if (hasNoRecords()) 1 else transactions.size
        return listOf(
            "",
            "",
            "=SUM(C$startLine:C$endLine)",
            "",
            TransactionHistoryPageMetadata.joinAccountNumber(accountNumber, classification),
            "",
            date
        ).joinToString(",")
    }

    /**
     * CALCULATING SUSPICIOUS REASONS
     */
    @JsonIgnore
    private val otherSuspiciousReasons = mutableListOf<String>()

    @JsonIgnore
    fun isSuspicious(): Boolean {
        return getSuspiciousReasons().isNotEmpty()
    }

    @JsonIgnore
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

    @JsonIgnore
    fun hasSuspiciousRecords(): Boolean = transactions.find { it.isSuspicious() } != null

    @JsonIgnore
    fun getTransactionDatesOutsideOfStatement(): List<String> {
        if (statementDate == null) return listOf()
        val twoMonthsAgo = Calendar.getInstance().apply { time = statementDate; add(Calendar.MONTH, -2) }.time
        return transactions.filter { it.transactionDate != null && (it.transactionDate.after(statementDate) || it.transactionDate.before(twoMonthsAgo)) }
            .map { it.date!! }
    }

    @JsonIgnore
    fun hasRecordsWithIncorrectDates() = getTransactionDatesOutsideOfStatement().isNotEmpty()

    private fun numbersAddUpBank(): Boolean = beginningBalance?.plus(getNetTransactions()) == endingBalance
    private fun numbersAddUpCreditCard(): Boolean = beginningBalance?.minus(getNetTransactions()) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(interestCharged ?: ZERO) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(feesCharged ?: ZERO) == endingBalance ||
            beginningBalance?.minus(getNetTransactions())?.plus(interestCharged ?: ZERO)?.plus(feesCharged ?: ZERO) == endingBalance

    // for credit card statements, all the numbers become negative, so we test the absolute value (TODO: only do this for CC)
    // we also add in interest, fees, or both to see if they add up, because some statements include it in transactions and others don't
    private fun numbersAddUp(): Boolean {
        return when (statementType) {
            DocumentType.BANK -> numbersAddUpBank()
            DocumentType.CREDIT_CARD -> numbersAddUpCreditCard()
            else -> numbersAddUpBank() || numbersAddUpCreditCard()
        }
    }

    @JsonIgnore
    fun numbersDoNotAddUp(): Boolean = !numbersAddUp()

    @JsonIgnore
    fun hasNoRecords(): Boolean = transactions.isEmpty()

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
            Pair(BankStatement::numbersDoNotAddUp) { stmt ->
                val transactions = if (stmt.statementType == DocumentType.CREDIT_CARD) stmt.getNetTransactions().negate() else stmt.getNetTransactions()
                val expected = if (stmt.beginningBalance == null || stmt.endingBalance == null) null
                    else if (stmt.statementType == DocumentType.CREDIT_CARD) stmt.beginningBalance!! - stmt.endingBalance!!
                    else stmt.endingBalance!! - stmt.beginningBalance!!
                SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(stmt.beginningBalance, transactions, stmt.endingBalance, expected)
           },
            Pair(BankStatement::hasNoRecords) { stmt -> SuspiciousReasons.NO_TRANSACTIONS_FOUND },
            Pair(BankStatement::hasSuspiciousRecords) { stmt -> SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS },
            Pair(BankStatement::hasRecordsWithIncorrectDates) { stmt -> SuspiciousReasons.INCORRECT_DATES.format(stmt.getTransactionDatesOutsideOfStatement()) }
        )

        fun getFileName(accountNumber: String?, date: String?, classification: String, filename: String, pages: Collection<PdfPageData>? = null): String {
            val name = "$accountNumber:$classification:${date?.replace("/", "_")}"
            if (accountNumber != null && date != null) {
                return "$name.json"
            } else {
                val fileNameSuffix = pages?.takeIf { it.isNotEmpty() }?.sortedBy { it.page }?.let { "${it.first().name}[${it.first().page}-${it.last().page}]" } ?: filename
                return "$name:$fileNameSuffix.json"
            }
        }
    }
}