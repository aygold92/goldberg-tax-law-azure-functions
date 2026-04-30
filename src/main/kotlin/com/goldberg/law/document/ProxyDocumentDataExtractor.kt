package com.goldberg.law.document

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawal
import com.goldberg.law.document.model.input.tables.TransactionTableDepositWithdrawalRecord
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.proxy.ProxyAccountGroup
import com.goldberg.law.document.proxy.ProxyClassificationSpec
import com.goldberg.law.document.proxy.ProxyFileSpec
import com.goldberg.law.document.proxy.ProxyFilenameParser
import io.github.oshai.kotlinlogging.KotlinLogging
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import kotlin.random.Random

/**
 * A local-only stand-in for [AzureDocumentDataExtractor] that generates deterministic
 * fake statement and check data from the filename, requiring no Azure credentials.
 *
 * Enable via env var: UseProxyDocumentIntelligence=true
 *
 * See [com.goldberg.law.document.proxy.ProxyFilenameParser] for filename format.
 *
 * Balance rules:
 *   - Each account group gets its own seeded beginning balance.
 *   - endingBalance = beginningBalance + sum(transactions).
 *   - If a statement is marked suspicious, a random incorrect ending balance is reported,
 *     but the *real* ending balance is silently carried forward to the next month.
 *
 * Check amounts are deterministic per check number (seeded by check number alone),
 * so the amount in a statement's check transaction always matches the corresponding
 * check page — regardless of which order they are extracted.
 */
class ProxyDocumentDataExtractor : DocumentDataExtractor() {
    private val logger = KotlinLogging.logger {}

    private val DATE_FMT = DateTimeFormatter.ofPattern("M/d/yyyy")

    private val DESCRIPTIONS = listOf(
        "Grocery Store", "Electric Bill", "Restaurant", "Online Purchase",
        "Gas Station", "Insurance Payment", "Coffee Shop", "ATM Withdrawal",
        "Phone Bill", "Streaming Service", "Hardware Store", "Pharmacy",
        "Department Store", "Water Bill", "Internet Service", "Gym Membership",
    )

    // ── Internal data types ────────────────────────────────────────────────────

    private data class SpecWithContext(
        val pageNum: Int,
        val accountGroup: ProxyAccountGroup,
        val classSpec: ProxyClassificationSpec,
        /** Non-null for StatementSpec; null for CheckPageSpec. */
        val statementDate: LocalDate?,
    )

    private data class StatementResult(
        val beginningBalance: BigDecimal,
        val reportedEndingBalance: BigDecimal,
        val transactions: List<TransactionTableDepositWithdrawalRecord>,
        val statementDate: LocalDate,
        val accountGroup: ProxyAccountGroup,
    )

    // ── Public API ─────────────────────────────────────────────────────────────

    override fun extractStatementData(classifiedDocument: ClassifiedPdfDocument): StatementDataModel {
        val spec = ProxyFilenameParser.parse(classifiedDocument.fileName)
        val pageNum = classifiedDocument.classification.pagesOrdered.first()
        val random = Random(spec.seed.hashCode().toLong())

        val result = simulateToStatement(spec, pageNum, random)
            ?: error("[Proxy Extractor] Page $pageNum is not a statement in ${classifiedDocument.fileName}")

        logger.info { "[Proxy Extractor] Statement page $pageNum → account ${result.accountGroup.accountNumber}, date ${result.statementDate}, ${result.transactions.size} txns" }

        return StatementDataModel(
            documentType = classifiedDocument.classification.classificationType,
            date = result.statementDate.format(DATE_FMT),
            accountNumber = result.accountGroup.accountNumber,
            beginningBalance = result.beginningBalance,
            endingBalance = result.reportedEndingBalance,
            feesCharged = null,
            interestCharged = null,
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(result.transactions),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            batesStampsTable = null,
            classification = classifiedDocument.classification,
        )
    }

    override fun extractCheckData(classifiedDocument: ClassifiedPdfDocument): CheckDataModel {
        val spec = ProxyFilenameParser.parse(classifiedDocument.fileName)
        val pageNum = classifiedDocument.classification.pagesOrdered.first()

        val entry = buildSpecsWithContext(spec).find { it.pageNum == pageNum }
            ?: error("[Proxy Extractor] Page $pageNum not found in ${classifiedDocument.fileName}")
        val checkSpec = entry.classSpec as? ProxyClassificationSpec.CheckPageSpec
            ?: error("[Proxy Extractor] Page $pageNum is not a check page in ${classifiedDocument.fileName}")

        logger.info { "[Proxy Extractor] Check page $pageNum → checks ${checkSpec.checkNumbers}" }
        return buildCheckModel(entry.accountGroup, checkSpec, classifiedDocument)
    }

    // ── Simulation ─────────────────────────────────────────────────────────────

    /**
     * Walks every classification in spec order, consuming [random] in a consistent
     * sequence, until we reach [targetPageNum].  Returns null if that page is a check page.
     */
    private fun simulateToStatement(spec: ProxyFileSpec, targetPageNum: Int, random: Random): StatementResult? {
        var beginningBalance: BigDecimal = BigDecimal.ZERO
        var currentAccountNumber: String? = null

        for (entry in buildSpecsWithContext(spec)) {
            // Fresh beginning balance at the start of each account group
            if (entry.accountGroup.accountNumber != currentAccountNumber) {
                beginningBalance = randomAmount(random, 1_000, 50_000)
                currentAccountNumber = entry.accountGroup.accountNumber
            }

            when (val classSpec = entry.classSpec) {
                is ProxyClassificationSpec.StatementSpec -> {
                    val transactions = generateTransactions(classSpec, entry.statementDate!!, random)
                    val transactionSum = transactions.sumOf {
                        (it.depositAmount ?: BigDecimal.ZERO).subtract(it.withdrawalAmount ?: BigDecimal.ZERO)
                    }
                    val realEnding = beginningBalance.add(transactionSum)
                    val reportedEnding = if (classSpec.suspicious) {
                        randomAmount(random, 1_000, 50_000)
                    } else {
                        realEnding
                    }

                    if (entry.pageNum == targetPageNum) {
                        return StatementResult(
                            beginningBalance = beginningBalance,
                            reportedEndingBalance = reportedEnding,
                            transactions = transactions,
                            statementDate = entry.statementDate,
                            accountGroup = entry.accountGroup,
                        )
                    }

                    // Carry the *real* ending balance forward regardless of suspiciousness
                    beginningBalance = realEnding
                }

                is ProxyClassificationSpec.CheckPageSpec -> {
                    // Check pages consume no random state
                    if (entry.pageNum == targetPageNum) return null
                }
            }
        }
        error("[Proxy Extractor] Page $targetPageNum not found in spec")
    }

    // ── Helpers ────────────────────────────────────────────────────────────────

    /**
     * Assigns a sequential page number and statement date to every classification spec.
     * Only StatementSpec items advance the month counter; CheckPageSpec items do not.
     */
    private fun buildSpecsWithContext(spec: ProxyFileSpec): List<SpecWithContext> {
        var pageNum = 1
        val result = mutableListOf<SpecWithContext>()
        spec.accountGroups.forEach { group ->
            var monthOffset = 0
            group.classificationSpecs.forEach { classSpec ->
                val date = when (classSpec) {
                    is ProxyClassificationSpec.StatementSpec ->
                        group.startDate.plusMonths(monthOffset.toLong()).also { monthOffset++ }
                    is ProxyClassificationSpec.CheckPageSpec -> null
                }
                result.add(SpecWithContext(pageNum, group, classSpec, date))
                pageNum++
            }
        }
        return result
    }

    /**
     * Generates transaction records for one statement.
     *
     * Random consumption per transaction (must be identical to [simulateToStatement]'s
     * walk-through so the balance simulation stays in sync):
     *   - Non-check transaction: nextBoolean (deposit?) + nextAmount + nextInt (day)
     *   - Check transaction:     nextInt (day)  — amount is deterministic via check number
     *   - If suspicious:         nextAmount for the fake ending balance
     */
    private fun generateTransactions(
        spec: ProxyClassificationSpec.StatementSpec,
        statementDate: LocalDate,
        random: Random,
    ): List<TransactionTableDepositWithdrawalRecord> {
        val records = mutableListOf<TransactionTableDepositWithdrawalRecord>()

        val nonCheckCount = (spec.transactionCount - spec.checkNumbers.size).coerceAtLeast(0)

        repeat(nonCheckCount) {
            val isDeposit = random.nextBoolean()
            val amount = randomAmount(random, 10, 2_000)
            val day = randomDay(random, statementDate)
            val description = DESCRIPTIONS[random.nextInt(DESCRIPTIONS.size)]
            records.add(
                TransactionTableDepositWithdrawalRecord(
                    date = statementDate.withDayOfMonth(day).format(DATE_FMT),
                    checkNumber = null,
                    description = description,
                    depositAmount = if (isDeposit) amount else null,
                    withdrawalAmount = if (!isDeposit) amount else null,
                    page = 1,
                )
            )
        }

        spec.checkNumbers.forEach { checkNum ->
            val day = randomDay(random, statementDate)
            records.add(
                TransactionTableDepositWithdrawalRecord(
                    date = statementDate.withDayOfMonth(day).format(DATE_FMT),
                    checkNumber = checkNum,
                    description = null,
                    depositAmount = null,
                    withdrawalAmount = deterministicCheckAmount(checkNum),
                    page = 1,
                )
            )
        }

        return records
    }

    private fun buildCheckModel(
        accountGroup: ProxyAccountGroup,
        checkSpec: ProxyClassificationSpec.CheckPageSpec,
        classifiedDocument: ClassifiedPdfDocument,
    ): CheckDataModel {
        val refDate = accountGroup.startDate.format(DATE_FMT)

        return if (checkSpec.checkNumbers.size == 1) {
            val checkNum = checkSpec.checkNumbers[0]
            CheckDataModel(
                accountNumber = accountGroup.accountNumber,
                checkNumber = checkNum,
                to = "Vendor #$checkNum",
                description = "Check payment",
                date = refDate,
                amount = deterministicCheckAmount(checkNum),
                checkEntries = null,
                batesStamp = null,
                classification = classifiedDocument.classification,
            )
        } else {
            // Multiple checks on one page → use CheckEntriesTable
            val rows = checkSpec.checkNumbers.map { checkNum ->
                CheckEntriesTableRow(
                    date = refDate,
                    checkNumber = checkNum,
                    to = "Vendor #$checkNum",
                    description = "Check payment",
                    amount = deterministicCheckAmount(checkNum),
                    accountNumber = accountGroup.accountNumber,
                    page = 1,
                )
            }
            CheckDataModel(
                accountNumber = accountGroup.accountNumber,
                checkNumber = null,
                to = null,
                description = null,
                date = null,
                amount = null,
                checkEntries = CheckEntriesTable(rows),
                batesStamp = null,
                classification = classifiedDocument.classification,
            )
        }
    }

    /**
     * Produces a stable amount for a given check number — independent of the
     * main Random stream so statement transactions and check pages always agree.
     */
    private fun deterministicCheckAmount(checkNumber: Int): BigDecimal =
        randomAmount(Random(checkNumber.toLong()), 50, 2_000)

    private fun randomAmount(random: Random, minDollars: Int, maxDollars: Int): BigDecimal {
        val cents = random.nextInt(minDollars * 100, maxDollars * 100 + 1)
        return BigDecimal(cents).divide(BigDecimal("100"), 2, RoundingMode.HALF_UP)
    }

    /** Returns a valid day-of-month within [statementDate]'s month. */
    private fun randomDay(random: Random, statementDate: LocalDate): Int =
        random.nextInt(1, statementDate.lengthOfMonth() + 1)
}
