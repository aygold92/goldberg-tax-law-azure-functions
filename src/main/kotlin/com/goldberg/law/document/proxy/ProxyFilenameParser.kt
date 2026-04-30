package com.goldberg.law.document.proxy

import java.time.LocalDate
import java.time.format.DateTimeFormatter

/**
 * Parses proxy test filenames into a structured [ProxyFileSpec].
 *
 * Format: {seed}x{accountNumber}x{MM-dd-yyyy}x{classifications}[x{accountNumber}x{MM-dd-yyyy}x{classifications}...]
 *
 * Classifications are underscore-delimited. Each item is one of:
 *   - t{N}[c{checkNum}...][s]  — bank statement:       t5c100s  = 5 txns, check #100, suspicious
 *   - T{N}[c{checkNum}...][s]  — credit card statement: T3       = 3 txns, not suspicious
 *   - c{checkNum}[c{checkNum}...]  — check page:        c100c101 = page with checks #100 and #101
 *
 * The date applies to the first statement in each account group; subsequent statements
 * auto-increment by one month. Check pages do not advance the month counter.
 *
 * Example:
 *   testx9088x08-01-2024xt5c100s_c100_t0_t4c101c102.pdf
 */
object ProxyFilenameParser {
    private val DATE_FORMAT = DateTimeFormatter.ofPattern("MM-dd-yyyy")
    private val STATEMENT_REGEX = Regex("""^([tT])(\d+)((?:c\d+)*)([sS]?)$""")
    private val CHECK_PAGE_REGEX = Regex("""^(c\d+)+$""")
    private val CHECK_NUM_REGEX = Regex("""c(\d+)""")

    fun parse(filename: String): ProxyFileSpec {
        val stem = filename.substringAfterLast("/").removeSuffix(".pdf")
        val parts = stem.split("x")

        require(parts.size >= 4) {
            "Proxy filename must contain at least seed, accountNumber, date, and classifications separated by 'x': $filename"
        }

        val seed = parts[0]
        val accountGroups = mutableListOf<ProxyAccountGroup>()

        var i = 1
        while (i + 2 <= parts.lastIndex) {
            val accountNumber = parts[i]
            val startDate = LocalDate.parse(parts[i + 1], DATE_FORMAT)
            val specs = parseClassifications(parts[i + 2])
            accountGroups.add(ProxyAccountGroup(accountNumber, startDate, specs))
            i += 3
        }

        require(accountGroups.isNotEmpty()) { "No account groups parsed from filename: $filename" }

        return ProxyFileSpec(seed, accountGroups)
    }

    private fun parseClassifications(classificationsStr: String): List<ProxyClassificationSpec> =
        classificationsStr.split("_").map { parseOneClassification(it) }

    private fun parseOneClassification(spec: String): ProxyClassificationSpec {
        val statementMatch = STATEMENT_REGEX.matchEntire(spec)
        if (statementMatch != null) {
            val (typeChar, countStr, checksStr, suspiciousStr) = statementMatch.destructured
            val checkNumbers = CHECK_NUM_REGEX.findAll(checksStr).map { it.groupValues[1].toInt() }.toList()
            return ProxyClassificationSpec.StatementSpec(
                isCreditCard = typeChar == "T",
                transactionCount = countStr.toInt(),
                checkNumbers = checkNumbers,
                suspicious = suspiciousStr.isNotEmpty()
            )
        }

        if (CHECK_PAGE_REGEX.matches(spec)) {
            val checkNumbers = CHECK_NUM_REGEX.findAll(spec).map { it.groupValues[1].toInt() }.toList()
            return ProxyClassificationSpec.CheckPageSpec(checkNumbers)
        }

        throw IllegalArgumentException("Cannot parse proxy classification spec: '$spec'")
    }
}
