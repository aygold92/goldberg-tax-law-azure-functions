package com.goldberg.law.util

import com.azure.core.exception.HttpResponseException
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.commons.lang3.exception.ExceptionUtils
import kotlin.math.pow
import kotlin.math.roundToLong
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

class RetryCalculator(
    private val initialIntervals: List<Long> = listOf(1000, 2000, 5000, 8000, 13000, 21000),
    val maxRetries: Int = 5,
    private val power: Double = 1.2,
) {
    private fun getSleepTime(attempt: Int): Long {
        if (attempt < initialIntervals.size) return initialIntervals[attempt]

        val extraAttempts = initialIntervals.size - attempt + 1

        return initialIntervals.last() + (extraAttempts) * power.pow(extraAttempts.toDouble()).roundToLong()
    }

    fun getSleepTimeWithJitter(attempt: Int): Long = createJitter(getSleepTime(attempt))

    private fun createJitter(time: Long): Long {
        val (low, high) = Pair((time * .9).roundToLong(), (time * 1.1).roundToLong())
        return low + (Math.random() * (high - low)).roundToLong()
    }
}

data class RetryContext(var attempt: Int = 0)

fun <T> retryWithBackoff(
    function: () -> T,
    retryCondition: (Throwable) -> Boolean,
    retryCalculator: RetryCalculator = RetryCalculator(),
    context: RetryContext = RetryContext()
): T {
    return try {
        function()
    } catch (t: Throwable) {
        if (!retryCondition(t)) throw t
        if (context.attempt >= retryCalculator.maxRetries) {
            throw RetryLimitExceededException("Exceeded retry limit of ${retryCalculator.maxRetries}", t)
        }
        context.attempt++
        val sleepTime = retryCalculator.getSleepTimeWithJitter(context.attempt)
        logger.trace { "Sleeping $sleepTime ms on ${context.attempt}. Caught error: $t" }
        Thread.sleep(sleepTime)
        return retryWithBackoff(function, retryCondition, retryCalculator, context)
    }
}

class RetryLimitExceededException(message: String, cause: Throwable): RuntimeException(message, cause)

fun isAzureThrottlingError(t: Throwable): Boolean {
    val rootCause = ExceptionUtils.getRootCause(t)
    return rootCause is HttpResponseException && rootCause.response.statusCode == 429
}