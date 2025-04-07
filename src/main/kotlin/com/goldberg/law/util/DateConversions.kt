package com.goldberg.law.util

import io.github.oshai.kotlinlogging.KotlinLogging
import java.text.ParseException
import java.text.SimpleDateFormat
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalField
import java.util.*

private val logger = KotlinLogging.logger {}
private val acceptedDateFormats = listOf(
    "MMMM d yyyy", // April 7 2020
    "MMMM d yyyy", // April 07 2020
    "MMM d yyyy",  // Apr 7 2020
    "MMM dd yyyy", // Apr 07 2020
    "MM dd yyyy",  // 04 07 2020
    "M d yyyy",    // 4 7 2020
    "MM dd yy",    // 04 07 20
    "M d yy",      // 4 7 20
    "yyyyMMdd",    // 20200407
    "yyyy MMM d",  // 2020 Apr 7
    "yyyy MMM dd", // 2020 Apr 07
)

private val TRANSACTION_DATE_FORMAT = SimpleDateFormat("M/d/yyyy")
fun Date.toTransactionDate(): String = TRANSACTION_DATE_FORMAT.format(this)
private val MONTH_YEAR_FORMAT = SimpleDateFormat("M/yyyy")
fun Date.toMonthYear(): String = MONTH_YEAR_FORMAT.format(this)
fun Date.getYearSafe(): String = getYearInt().toString()
fun Date.getYearInt(): Int = Calendar.getInstance().apply { time = this@getYearInt }.get(Calendar.YEAR)
fun Date.getMonthInt(): Int = Calendar.getInstance().apply { time = this@getMonthInt }.get(Calendar.MONTH)
private fun Date.adjustYear(): Date = Calendar.getInstance().apply { time = this@adjustYear }.let { cal ->
    val year = cal.get(Calendar.YEAR)
    if (year < 100) cal.set(Calendar.YEAR, year + 2000)
    cal.time
}

fun normalizeDate(dateString: String?): String? = fromWrittenDate(dateString)?.toTransactionDate()

fun fromWrittenDate(monthDay: String?, year: String?): Date? =
    if (monthDay == null || year == null) null
    else fromWrittenDate("$monthDay $year")

fun fromWrittenDate(monthDayYear: String?): Date? {
    if (monthDayYear == null) return null
    // convert all dates to standard format like "month day year" (removing excess space and non alphanumeric characters)
    // the date should then match one of the accepted formats
    val dateString = monthDayYear
        .map { if (it.isLetterOrDigit() || it.isWhitespace()) it else ' ' }
        .joinToString("")
        .replace("\\s+".toRegex(), " ").trim()
    acceptedDateFormats.forEach { format ->
        try {
            return SimpleDateFormat(format).parse(dateString).adjustYear()
        } catch (e: ParseException) {
            // Continue to the next format
        }
    }
    logger.error { "Unable to parse date string: $dateString" }
    return null
}
