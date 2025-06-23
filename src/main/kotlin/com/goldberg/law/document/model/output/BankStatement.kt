package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.function.model.metadata.StatementMetadata
import com.goldberg.law.util.*
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.util.*
import com.goldberg.law.document.model.input.StatementDataModel.Keys as FieldKeys

@JsonIgnoreProperties(ignoreUnknown = true, value = ["netTransactions", "totalSpending", "totalIncomeCredits", "suspiciousReasons"], allowGetters = true)
data class BankStatement @JsonCreator constructor(
    @JsonProperty("pageMetadata")
    val pageMetadata: ClassifiedPdfMetadata,
    @JsonProperty("date")
    val date: String?,
    @JsonProperty("accountNumber")
    val accountNumber: String? = null,
    @JsonProperty("beginningBalance")
    val beginningBalance: BigDecimal? = null,
    @JsonProperty("endingBalance")
    val endingBalance: BigDecimal? = null,
    // sometimes interest and fees are included in transactions, other times not, so we include them here
    @JsonProperty("interestCharged")
    val interestCharged: BigDecimal? = null,
    @JsonProperty("feesCharged")
    val feesCharged: BigDecimal? = null,
    @JsonProperty("transactions")
    val transactions: List<TransactionHistoryRecord>,
    @JsonProperty("batesStamps")
    val batesStamps: Map<Int, String>,
    @JsonProperty("checks")
    val checks: MutableMap<Int, ClassifiedPdfMetadata> = mutableMapOf()
) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)
    @JsonIgnore @Transient
    val statementType: DocumentType? = pageMetadata.documentType
    @JsonIgnore @Transient
    private val logger = KotlinLogging.logger {}
    @JsonIgnore @Transient
    val primaryKey = BankStatementKey(date, accountNumber, pageMetadata.classification)

    @JsonIgnore
    fun azureFileName() = getFileName(accountNumber, date, pageMetadata.classification, pageMetadata.filename, pageMetadata.getPageRange())

    @JsonProperty("netTransactions")
    fun getNetTransactions(): BigDecimal = getTotalIncomeCredits() - getTotalSpending()

    // for credit card statements, all the numbers are reversed so we need to negate
    @JsonProperty("totalSpending")
    fun getTotalSpending() = transactions
        .map { it.amount ?: 0.asCurrency() }
        .filter { it < 0.asCurrency() }
        .takeIf { it.isNotEmpty() }
        ?.reduce {acc, amt -> (acc + amt) }?.abs() ?: 0.asCurrency()

    @JsonProperty("totalIncomeCredits")
    fun getTotalIncomeCredits(): BigDecimal = transactions
        .map { it.amount ?: 0.asCurrency() }
        .filter { it > 0.asCurrency() }
        .takeIf { it.isNotEmpty() }
        ?.reduce {acc, amt -> (acc + amt) }?.abs() ?: 0.asCurrency()

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
        pageMetadata.classification,
        statementDate,
        beginningBalance,
        endingBalance,
        getNetTransactions(),
        transactions.size,
        batesStamps.values.toSet(),
        pageMetadata.filename,
        pageMetadata.pages,
        isSuspicious(),
        getSuspiciousReasons()
    )

    fun toCsv(): String = transactions.sortedBy { it.transactionDate }.joinToString("\n") {
        it.toCsv(date, accountNumber, pageMetadata, batesStamps)
    }

    fun getStatementMetadata(manuallyVerified: Boolean) = StatementMetadata(
        this.md5Hash(),
        this.isSuspicious(),
        this.getMissingChecks().isNotEmpty(),
        manuallyVerified,
        this.statementType,
        this.getTotalSpending(),
        this.getTotalIncomeCredits(),
        this.transactions.size,
        this.pageMetadata.filename,
        this.pageMetadata.getPageRange()
    )

    fun suspiciousReasonsToCsv(): String {
        // add suspicious reasons, and include the account and statementDate
        // NOTE: be very careful as this must line up with the headers defined in TransactionHistoryRecord
        val suspiciousReasons = getSuspiciousReasons()
        return listOf(
                if (suspiciousReasons.isNotEmpty()) suspiciousReasons.toString().addQuotes() else "Verified",
                "",
                "",
                "",
                joinAccountNumber(accountNumber, pageMetadata.classification),
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
            joinAccountNumber(accountNumber, pageMetadata.classification),
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

    @JsonProperty("suspiciousReasons")
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

    // for some reason when you do math on BigDecimal it might add trailing 0s, so we need to get rid of them
    private fun numbersAddUpBank(beginningBalance: BigDecimal, endingBalance: BigDecimal): Boolean =
        (beginningBalance + getNetTransactions()).stripTrailingZeros() == endingBalance.stripTrailingZeros()

    // net transactions are reversed on a credit card since spending (positive balance on the physical statement) is stored as a negative cash flow
    // for that reason we subtract the net transactions to get to endingBalance
    //
    // we then add in interest, fees, or both to see if they add up, because some statements include it in transactions and others don't
    // TODO: maybe some logic to check if it's already included?
    private fun numbersAddUpCreditCard(beginningBalance: BigDecimal, endingBalance: BigDecimal): Boolean = getNetTransactions().negate().let { netTransactions ->
        val interest = interestCharged ?: ZERO
        val fees = feesCharged ?: ZERO
        val endBalance = endingBalance.stripTrailingZeros()
        // we've already ensured not null on beginningBalance/endingBalance when we call this function
        (beginningBalance + netTransactions).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + interest).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + fees).stripTrailingZeros() == endBalance ||
                (beginningBalance + netTransactions + interest + fees).stripTrailingZeros() == endBalance
    }


    private fun numbersAddUp(): Boolean {
        // the statement will be flagged as suspicious already for null values, we don't care about this here
        if (beginningBalance == null || endingBalance == null) return true
        return when (statementType) {
            DocumentType.BANK -> numbersAddUpBank(beginningBalance, endingBalance)
            DocumentType.CREDIT_CARD -> numbersAddUpCreditCard(beginningBalance, endingBalance)
            else -> numbersAddUpBank(beginningBalance, endingBalance) || numbersAddUpCreditCard(beginningBalance, endingBalance)
        }
    }

    @JsonIgnore
    fun numbersDoNotAddUp(): Boolean = !numbersAddUp()

    @JsonIgnore
    fun hasNoRecords(): Boolean = transactions.isEmpty()

    @JsonIgnore
    fun isCreditCard(): Boolean = statementType == DocumentType.CREDIT_CARD

    @JsonIgnore
    fun isNFCUBank(): Boolean = pageMetadata.classification == DocumentType.BankTypes.NFCU_BANK

    object SuspiciousReasons {
        const val MISSING_FIELDS = "Missing fields: %s"
        const val BALANCE_DOES_NOT_ADD_UP = "Beginning balance (%s) + net transactions (%s) != ending balance (%s). Expected (%s)"
        const val NO_TRANSACTIONS_FOUND = "No transactions recorded"
        const val CONTAINS_SUSPICIOUS_RECORDS = "Contains suspicious records"
        const val INCORRECT_DATES = "Found transactions with dates outside of this statement: %s"
    }

    companion object {
        private val suspiciousReasonMap: List<Pair<(BankStatement) -> Boolean, (BankStatement) -> String>> = listOf(
            Pair(BankStatement::hasMissingFields) { stmt -> SuspiciousReasons.MISSING_FIELDS.format(stmt.getMissingFields().toString()) },
            Pair(BankStatement::numbersDoNotAddUp) { stmt ->
                val expected = if (stmt.beginningBalance == null || stmt.endingBalance == null) null
                    else if (stmt.isCreditCard()) stmt.beginningBalance.asCurrency()!! - stmt.endingBalance.asCurrency()!!
                    else stmt.endingBalance - stmt.beginningBalance
                SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(stmt.beginningBalance?.toCurrency(), stmt.getNetTransactions().toCurrency(), stmt.endingBalance?.toCurrency(), expected?.toCurrency())
           },
            Pair(BankStatement::hasNoRecords) { _ -> SuspiciousReasons.NO_TRANSACTIONS_FOUND },
            Pair(BankStatement::hasSuspiciousRecords) { _ -> SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS },
            Pair(BankStatement::hasRecordsWithIncorrectDates) { stmt -> SuspiciousReasons.INCORRECT_DATES.format(stmt.getTransactionDatesOutsideOfStatement()) }
        )

        // TODO: filename on BankStatement is one value but in PDF Pages it's multiple values.  Technically a bank statement could come from multiple files although in practice probably not
        fun getFileName(accountNumber: String?, date: String?, classification: String, filename: String, pageRange: Pair<Int, Int>? = null): String {
            val name = "$accountNumber:$classification:${date?.replace("/", "_")}"
            if (accountNumber != null && date != null) {
                return "$name.json"
            } else {
                val fileNameSuffix = if (pageRange != null) "${filename}[${pageRange.first}-${pageRange.second}]" else filename
                return "$name:$fileNameSuffix.json"
            }
        }

        private const val UNKNOWN_ACCOUNT = "Unknown"

        fun joinAccountNumber(accountNumber: String?, classification: String) =
            "$classification - ${accountNumber ?: UNKNOWN_ACCOUNT}".addQuotes()
    }
}