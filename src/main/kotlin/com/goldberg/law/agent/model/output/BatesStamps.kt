package com.goldberg.law.agent.model.output

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/**
 * Flattens the splitter's compact report into the page -> stamp map the rest of the app stores: statements
 * and checks each keep the stamps for their own pages, and the file keeps the lot.
 *
 * A sequence is stated as its endpoints and one stamp per page in between, so the pages between them are
 * filled in by incrementing the numeric tail of `first_stamp` and keeping its width ("MH-000001" + 11 ->
 * "MH-000012"). A sequence whose arithmetic doesn't land on `last_stamp` is trusted only at its endpoints.
 */
fun BatesReport.toPageStamps(): Map<Int, String> {
    val stamps = sortedMapOf<Int, String>()
    sequences.forEach { stamps.putAll(it.toPageStamps()) }
    // A page named outright wins over one derived from a sequence
    stamps.putAll(nonSequenced)
    return stamps
}

private fun BatesSequence.toPageStamps(): Map<Int, String> {
    val endpoints = mapOf(start to firstStamp, end to lastStamp)
    if (end <= start) return endpoints

    val prefix = firstStamp.dropLastWhile { it.isDigit() }
    val digits = firstStamp.substring(prefix.length)
    if (digits.isEmpty()) {
        logger.warn { "Bates stamp '$firstStamp' (pages $start-$end) has no number to advance; using its endpoints only" }
        return endpoints
    }

    val firstNumber = digits.toLong()
    fun stampFor(page: Int) = prefix + (firstNumber + page - start).toString().padStart(digits.length, '0')

    val computedLast = stampFor(end)
    if (computedLast != lastStamp) {
        logger.warn {
            "Bates sequence $firstStamp..$lastStamp over pages $start-$end doesn't advance by one per page " +
                "(page $end would be $computedLast); using its endpoints only"
        }
        return endpoints
    }
    return (start..end).associateWith { stampFor(it) }
}
