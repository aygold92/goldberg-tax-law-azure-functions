package com.goldberg.law.util

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KotlinExtensionsTest {
    @ParameterizedTest
    @ValueSource(strings = ["50.0", "50.00", "50", "$50", "$50.00", "$50.0", "+$50.00"])
    fun testParseCurrencyPositive(value: String) {
        assertThat(value.parseCurrency()).isEqualTo(50.0)
    }

    @ParameterizedTest
    @ValueSource(strings = ["-50.0", "-50.00", "-50", "-$50", "-$50.00", "-$50.0", " - 50.0 * "])
    fun testParseCurrencyNegative(value: String) {
        assertThat(value.parseCurrency()).isEqualTo(-50.0)
    }

    @Test
    fun testGetFileName() {
        assertThat("./testInput/nonexistent/12345.asdknalksdn/test.pdf".getFilename()).isEqualTo("test")
    }
}