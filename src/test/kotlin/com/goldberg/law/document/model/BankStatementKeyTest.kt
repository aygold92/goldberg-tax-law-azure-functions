package com.goldberg.law.document.model

import com.goldberg.law.document.model.output.BankStatementKey
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BankStatementKeyTest {
    @Test
    fun testIsComplete() {
        val date = normalizeDate("4/3 2020")
        val account = "01234560"
        assertThat(BankStatementKey(null, "Type", date).isComplete()).isFalse()
        assertThat(BankStatementKey(null, "Type", null).isComplete()).isFalse()
        assertThat(BankStatementKey(account, "Type", null).isComplete()).isFalse()
        assertThat(BankStatementKey(account, "Type", date).isComplete()).isTrue()
    }
}