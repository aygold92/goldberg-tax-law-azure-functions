package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.BASIC_BANK_STATEMENT
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.output.CheckDataKey
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountNormalizerTest {
    val accountNormalizer = AccountNormalizer()

    @Test
    fun testNormalizeAlreadyNormalized() {
        val statements = listOf(newBankStatement("1234"), newBankStatement("5678"), newBankStatement("1230"))
        val checks = mapOf(
            CheckDataKey("1234", 1000) to newCheckData(1000, accountNumber = "1234"),
            CheckDataKey("5678", 10001) to newCheckData(1001, accountNumber = "5678")
        )

        val (resultStatements, resultChecks) = accountNormalizer.normalizeAccounts(statements, checks)
        assertThat(resultStatements).isEqualTo(statements)
        assertThat(resultChecks).isEqualTo(checks)
    }

    @Test
    fun testNormalize() {
        val statements = listOf(newBankStatement("aa123"), newBankStatement("1234"), newBankStatement("xxx1234"), newBankStatement("00001234xx"), newBankStatement("xx99234xx"))
        val checks = mapOf(
            CheckDataKey("001234", 1000) to newCheckData(1000, accountNumber = "001234"),
            CheckDataKey("5678", 10001) to newCheckData(1001, accountNumber = "5678"),
            CheckDataKey("aa123", 10001) to newCheckData(1001, accountNumber = "aa123")
        )

        val (resultStatements, resultChecks) = accountNormalizer.normalizeAccounts(statements, checks)
        assertThat(resultStatements).isEqualTo(
            listOf(newBankStatement("aa123"), newBankStatement("00001234"), newBankStatement("00001234"), newBankStatement("00001234"), newBankStatement("99234"))
        )
        assertThat(resultChecks).isEqualTo(
            mapOf(
                CheckDataKey("00001234", 1000) to newCheckData(1000, accountNumber = "00001234"),
                CheckDataKey("5678", 10001) to newCheckData(1001, accountNumber = "5678"),
                CheckDataKey("aa123", 10001) to newCheckData(1001, accountNumber = "aa123")
            )
        )
    }

    @Test
    fun testNormalizeOverlappingAccounts() {
        val statements = listOf(newBankStatement("1234"), newBankStatement("xxx991234"), newBankStatement("00001234xx"))
        val checks = mapOf(
            CheckDataKey("001234", 1000) to newCheckData(1000, accountNumber = "001234"),
            CheckDataKey("5678", 10001) to newCheckData(1001, accountNumber = "5678")
        )

        val (resultStatements, resultChecks) = accountNormalizer.normalizeAccounts(statements, checks)
        assertThat(resultStatements).isEqualTo(
            listOf(newBankStatement("00001234"), newBankStatement("00001234"), newBankStatement("00001234"))
        )
        assertThat(resultChecks).isEqualTo(
            mapOf(
                CheckDataKey("00001234", 1000) to newCheckData(1000, accountNumber = "00001234"),
                CheckDataKey("5678", 10001) to newCheckData(1001, accountNumber = "5678")
            )
        )
    }

    @Test
    fun testAccountMapping() {
        assertThat(accountNormalizer.getAccountMapping(
            setOf("xxxx", "xxx123", "7890", "567890", "1234567890", "1234", "001234", "098001234", "xxx1234", "xxxx1234")
        )).isEqualTo(mapOf(
            "7890" to "1234567890",
            "567890" to "1234567890",
            "1234567890" to "1234567890",
            "1234" to "098001234",
            "xxx1234" to "098001234",
            "xxxx1234" to "098001234",
            "001234" to "098001234",
            "098001234" to "098001234"
        ))
    }
}