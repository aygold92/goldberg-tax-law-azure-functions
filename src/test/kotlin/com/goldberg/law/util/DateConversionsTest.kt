package com.goldberg.law.util

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class DateConversionsTest {
    @ParameterizedTest
    @ValueSource(strings = ["April 7", "April 7,", "April 7, ", "April 7,  "])
    fun testFromStatementDate(monthDay: String) {
        val date = fromStatementDate(monthDay, "2020")
        assertThat(date?.toTransactionDate()).isEqualTo("4/7/2020")
    }

    @ParameterizedTest
    @ValueSource(strings = ["4/7", "04/07"])
    fun testFromTransactionDate(monthDay: String) {
        val date = fromTransactionDate(monthDay, "2020")
        assertThat(date?.toTransactionDate()).isEqualTo("4/7/2020")
    }

    @Test
    fun testGetYear() {
        assertThat(fromTransactionDate("1/1", "2020")?.getYearSafe()).isEqualTo("2020")
        assertThat(fromStatementDate("December 31", "2020")?.getYearSafe()).isEqualTo("2020")
    }
}