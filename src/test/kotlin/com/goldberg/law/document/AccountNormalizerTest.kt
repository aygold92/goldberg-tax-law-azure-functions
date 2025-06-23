package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.newBatesStampTable
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class AccountNormalizerTest {
    private val accountNormalizer = AccountNormalizer()

    @Test
    fun testNormalizeAlreadyNormalized() {
        val models = listOf(newStatementDataModel("1234"), newStatementDataModel("5678"), newStatementDataModel("1230"))
        val checks = listOf(newCheckData(1000, accountNumber = "1234"), newCheckData(1001, accountNumber = "5678"))

        val (resultModels, resultChecks) = accountNormalizer.normalizeAccounts(models, checks)
        assertThat(resultModels).isEqualTo(models)
        assertThat(resultChecks).isEqualTo(checks)
    }

    @Test
    fun testNormalize() {
        val models = listOf(
            newStatementDataModel("aa123"),
            newStatementDataModel("1234"),
            newStatementDataModel("xxx1234"),
            newStatementDataModel("00001234xx"),
            newStatementDataModel("xx99234xx")
        )
        val checks = listOf(
            newCheckData(1000, accountNumber = "001234"),
            newCheckData(1001, accountNumber = "5678"),
            newCheckData(1001, accountNumber = "aa123"),
        )

        val (resultModels, resultChecks) = accountNormalizer.normalizeAccounts(models, checks)
        assertThat(resultModels).isEqualTo(
            listOf(
                newStatementDataModel("aa123"),
                newStatementDataModel("1234"),
                newStatementDataModel("1234"),
                newStatementDataModel("1234"),
                newStatementDataModel("9234")
            )
        )
        assertThat(resultChecks).isEqualTo(
            listOf(
                newCheckData(1000, accountNumber = "1234"),
                newCheckData(1001, accountNumber = "5678"),
                newCheckData(1001, accountNumber = "aa123")
            )
        )
    }

    // TODO: what if there are overlapping account numbers?  Really, it's impossible to know, you'd have to manually override.
    //  I don't think this really affects anything though as it doesn't affect the net transactions
    @Test
    fun testNormalizeOverlappingAccounts() {
        val models = listOf(
            newStatementDataModel("1234"),
            newStatementDataModel("xxx991234"),
            newStatementDataModel("00001234xx")
        )
        val checks = listOf(
            newCheckData(1000, accountNumber = "001234"),
            newCheckData(1001, accountNumber = "5678")
        )

        val (resultModels, resultChecks) = accountNormalizer.normalizeAccounts(models, checks)
        assertThat(resultModels).isEqualTo(
            listOf(
                newStatementDataModel("1234"),
                newStatementDataModel("1234"),
                newStatementDataModel("1234"))
        )
        assertThat(resultChecks).isEqualTo(
            listOf(
                newCheckData(1000, accountNumber = "1234"),
                newCheckData(1001, accountNumber = "5678")
            )
        )
    }

    @Test
    fun testAccountMapping() {
        assertThat(accountNormalizer.getAccountMapping(
            setOf("xxxx", "xxx123", "7890", "567890", "1234567890", "1234", "001234", "098001234", "xxx1234", "xxxx1234")
        )).isEqualTo(mapOf(
            "7890" to "7890",
            "567890" to "7890",
            "1234567890" to "7890",
            "1234" to "1234",
            "xxx1234" to "1234",
            "xxxx1234" to "1234",
            "001234" to "1234",
            "098001234" to "1234"
        ))
    }
    
    companion object {
        fun newStatementDataModel(accountNumber: String?) = StatementDataModel(
            date = FIXED_STATEMENT_DATE,
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStamps = newBatesStampTable(1 to BATES_STAMP),
            accountNumber = accountNumber,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FILENAME, 2, DocumentType.BankTypes.WF_BANK)
        ) 
    }
}