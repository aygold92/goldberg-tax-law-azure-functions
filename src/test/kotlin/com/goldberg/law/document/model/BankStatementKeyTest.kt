package com.goldberg.law.document.model

import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BankStatementKeyTest {
    @Test
    fun testIsComplete() {
        val date = fromWrittenDate("4/3", "2020")
        val account = "01234560"
        assertThat(BankStatementKey(date, null).isComplete()).isFalse()
        assertThat(BankStatementKey(null, null).isComplete()).isFalse()
        assertThat(BankStatementKey(null, account).isComplete()).isFalse()
        assertThat(BankStatementKey(date, account).isComplete()).isTrue()
    }
}