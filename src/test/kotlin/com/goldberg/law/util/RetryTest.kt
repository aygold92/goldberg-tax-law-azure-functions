package com.goldberg.law.util

import com.azure.core.exception.HttpResponseException
import com.azure.core.http.HttpResponse
import com.azure.core.implementation.http.BufferedHttpResponse
import com.goldberg.law.util.RetryCalculator
import com.goldberg.law.util.RetryContext
import com.goldberg.law.util.RetryLimitExceededException
import com.goldberg.law.util.retryWithBackoff
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock
import java.util.concurrent.ExecutionException

class RetryTest {
    private val retryCalculator = RetryCalculator(initialIntervals = listOf(100, 100, 100), maxRetries = 3)
    private val client = mock<MutableList<Int>>()

    @Mock
    val httpResponseException: HttpResponseException = mock()
    @Mock
    val httpResponse: HttpResponse = mock()

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

    @Test
    fun testAzureThrottlingError() {
        whenever(httpResponseException.response).thenReturn(httpResponse)
        whenever(httpResponse.statusCode).thenReturn(429)
        val context = RetryContext()
        assertThrows<RetryLimitExceededException> { retryWithBackoff({ throw httpResponseException }, ::isAzureThrottlingError, retryCalculator, context) }
        assertThat(context.attempt).isEqualTo(3)
    }

    @Test
    fun testAzureThrottlingErrorNested() {
        whenever(httpResponseException.response).thenReturn(httpResponse)
        whenever(httpResponse.statusCode).thenReturn(429)
        val context = RetryContext()
        assertThrows<RetryLimitExceededException> { retryWithBackoff({ throw ExecutionException(httpResponseException) }, ::isAzureThrottlingError, retryCalculator, context) }
        assertThat(context.attempt).isEqualTo(3)
    }

    @Test
    fun testAzureThrottlingErrorDoubleNested() {
        whenever(httpResponseException.response).thenReturn(httpResponse)
        whenever(httpResponse.statusCode).thenReturn(429)
        val context = RetryContext()
        assertThrows<RetryLimitExceededException> { retryWithBackoff({ throw RuntimeException(ExecutionException(httpResponseException)) }, ::isAzureThrottlingError, retryCalculator, context) }
        assertThat(context.attempt).isEqualTo(3)
    }
}