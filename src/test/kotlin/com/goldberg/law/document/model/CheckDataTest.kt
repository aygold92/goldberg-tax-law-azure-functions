package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.newCheckData
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class CheckDataTest {
    @Test
    fun toCsv() {
        assertThat(newCheckData(1000).toCsv())
            .isEqualTo("""
                "1234567890",1000,"test",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent())
    }
}