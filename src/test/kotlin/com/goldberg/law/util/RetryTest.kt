package com.goldberg.law.util

import com.goldberg.law.util.RetryCalculator
import com.goldberg.law.util.RetryContext
import com.goldberg.law.util.RetryLimitExceededException
import com.goldberg.law.util.retryWithBackoff
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RetryTest {
    private val retryCalculator = RetryCalculator(initialIntervals = listOf(10, 10, 10), maxRetries = 3)
    private val client = mock<MutableList<Int>>()

    @Test
    fun testRetryLimit() {
        val context = RetryContext()
        assertThrows<RetryLimitExceededException> { retryWithBackoff({ throw Exception() }, { it is Exception }, retryCalculator, context) }
        assertThat(context.attempt).isEqualTo(3)
    }

    @Test
    fun testRetryLimitMock() {
        val context = RetryContext()
        whenever(client.add(any())).thenThrow(RuntimeException())
        val func = { client.add(5) }
        assertThrows<RetryLimitExceededException> { retryWithBackoff(func, { it is Exception }, retryCalculator, context) }
        verify(client, times(4)).add(5)
        assertThat(context.attempt).isEqualTo(3)
    }

    @Test
    fun testRetryLimitMockFailImmediately() {
        val context = RetryContext()
        whenever(client.add(any())).thenThrow(RuntimeException())
        val func = { client.add(5) }
        assertThrows<RuntimeException> { retryWithBackoff(func, { false }, retryCalculator, context) }
        verify(client, times(1)).add(5)
        assertThat(context.attempt).isEqualTo(0)
    }
}