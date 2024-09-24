package com.goldberg.law.util

import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.time.Instant

class DateConversionsTest {
    @ParameterizedTest
    @ValueSource(strings = ["April 7 2020", "April 7, 2020", "April 7,2020", ".April 7, 2020", "April 7,  2020", "Apr 7, 2020",
        "4/7/2020", "04/07/2020", "4/7/20", "04/07/20", " .-4/7. 2020 ; "])
    fun testFromStatementDate(monthDayYear: String) {
        val date = fromWrittenDate(monthDayYear)
        assertThat(date?.toTransactionDate()).isEqualTo("4/7/2020")
        assertThat(normalizeDate(monthDayYear)).isEqualTo("4/7/2020")
    }

    @ParameterizedTest
    @ValueSource(strings = ["4/7", "04/07", "4/7/2020", "04/07/2020", "4/7/20", "04/07/20", ".-4/7.", "Apr 7", "April 7",
        "April 7 2020", "April 7, 2020", "April 7,2020", ".April 7, 2020", "April 7,  2020", "Apr 7, 2020",
        "4/7/2020", "04/07/2020", "4/7/20", "04/07/20", " .-4/7. 2020 ; "])
    fun testFromTransactionDate(monthDay: String) {
        val date = fromWrittenDate(monthDay, "2020")
        assertThat(date?.toTransactionDate()).isEqualTo("4/7/2020")
        assertThat(normalizeDate("$monthDay 2020")).isEqualTo("4/7/2020")
    }

    @Test
    fun testGetYear() {
        assertThat(fromWrittenDate("1/1", "2020")?.getYearSafe()).isEqualTo("2020")
        assertThat(fromWrittenDate("December 31", "2020")?.getYearSafe()).isEqualTo("2020")
    }
}