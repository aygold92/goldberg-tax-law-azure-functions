package com.goldberg.law.document.model

import com.goldberg.law.document.model.StatementModelValues.newClassifiedPdfDocument
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.CheckDataModel.Companion.toCheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow
import com.goldberg.law.entity.CheckDetails
import com.goldberg.law.entity.EntityValues.DEFAULT_ACCOUNT_NUMBER
import com.goldberg.law.entity.EntityValues.DEFAULT_BATES_STAMP
import com.goldberg.law.entity.EntityValues.entityCompare
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource
import java.util.UUID
import kotlin.test.Test

class CheckDataModelTest {
    @Test
    fun extractCompositeCheckData() {
        val compositeCheckData = CheckDataModel(
            DEFAULT_ACCOUNT_NUMBER,
            null,
            null,
            null,
            null,
            null,
            CheckEntriesTable(images = listOf(
                CheckEntriesTableRow(
                    normalizeDate("4 10 2020"),
                    1000,
                    "Some Guy",
                    "test",
                    1500.asCurrency(),
                    null,
                    page = 1
                ),
                CheckEntriesTableRow(
                    normalizeDate("4 15 2020"),
                    1001,
                    "Another Guy",
                    "desc",
                    2000.asCurrency(),
                    null,
                    page = 1
                ),
            )),
            DEFAULT_BATES_STAMP,
            newClassification()
        )
        val checkDetails1 = CheckDetails(
            checkId = UUID.randomUUID(),
            checkNumber = 1000,
            accountNumber = DEFAULT_ACCOUNT_NUMBER,
            description = "test",
            date = normalizeDate("4 10 2020"),
            amount = 1500.asCurrency(),
            to = "Some Guy",
            batesStamp = DEFAULT_BATES_STAMP,
        )
        val checkDetails2 = CheckDetails(
            checkId = UUID.randomUUID(),
            checkNumber = 1001,
            accountNumber = DEFAULT_ACCOUNT_NUMBER,
            description = "desc",
            date = normalizeDate("4 15 2020"),
            amount = 2000.asCurrency(),
            to = "Another Guy",
            batesStamp = DEFAULT_BATES_STAMP,
        )

        assertThat(compositeCheckData.toCheckDetails()).entityCompare()
            .isEqualTo(listOf(checkDetails1, checkDetails2))
    }

    @Test
    fun extractCompositeCheckDataWithAccountNumber() {
        val otherAccountNumber = "9876"
        val compositeCheckData = CheckDataModel(
            null,
            null,
            null,
            null,
            null,
            null,
            CheckEntriesTable(images = listOf(
                CheckEntriesTableRow(
                    normalizeDate("4 10 2020"),
                    1000,
                    "Some Guy",
                    "test",
                    1500.asCurrency(),
                    DEFAULT_ACCOUNT_NUMBER,
                    page = 1
                ),
                CheckEntriesTableRow(
                    normalizeDate("4 15 2020"),
                    1001,
                    "Another Guy",
                    "desc",
                    2000.asCurrency(),
                    otherAccountNumber,
                    page = 1
                ),
            )),
            DEFAULT_BATES_STAMP,
            newClassification()
        )
        val checkDetails1 = CheckDetails(
            checkId = UUID.randomUUID(),
            checkNumber = 1000,
            accountNumber = DEFAULT_ACCOUNT_NUMBER,
            description = "test",
            date = normalizeDate("4 10 2020"),
            amount = 1500.asCurrency(),
            to = "Some Guy",
            batesStamp = DEFAULT_BATES_STAMP,
        )
        val checkDetails2 = CheckDetails(
            checkId = UUID.randomUUID(),
            checkNumber = 1001,
            accountNumber = otherAccountNumber,
            description = "desc",
            date = normalizeDate("4 15 2020"),
            amount = 2000.asCurrency(),
            to = "Another Guy",
            batesStamp = DEFAULT_BATES_STAMP,
        )

        assertThat(compositeCheckData.toCheckDetails()).entityCompare()
            .isEqualTo(listOf(checkDetails1, checkDetails2))
    }

    // -- toCheckDataModel() --

    @ParameterizedTest
    @CsvSource(
        "1234567890, 7890",
        "****1234, 1234",
        "12-3456-7890, 7890",
        "9876, 9876",
        "123, 123",
        "*90-90*&1--, 0901",
    )
    fun `toCheckDataModel extracts last 4 digits of account number`(input: String, expected: String) {
        val model = AD(mapOf(
            CheckDataModel.Keys.ACCOUNT_NUMBER to DF.of(input)
        )).create().toCheckDataModel(newClassifiedPdfDocument())

        assertThat(model.accountNumber).isEqualTo(expected)
    }

    @Test
    fun `toCheckDataModel returns null when account number is missing`() {
        val model = AD(emptyMap()).create().toCheckDataModel(newClassifiedPdfDocument())

        assertThat(model.accountNumber).isNull()
    }

    @Test
    fun testFixAccountNumber() {
        // Some checks have the account number smashed together with the check number at the
        // bottom of the check, e.g. "8558⑈5563" where 5563 is the check number.
        // getAccountNumber() should strip the check number and extract the account number.
        val model = AD(mapOf(
            CheckDataModel.Keys.ACCOUNT_NUMBER to DF.of("8558⑈5563"),
            CheckDataModel.Keys.CHECK_NUMBER to DF.of(5563L),
        )).create().toCheckDataModel(newClassifiedPdfDocument())

        assertThat(model.accountNumber).isEqualTo("8558")
    }
}