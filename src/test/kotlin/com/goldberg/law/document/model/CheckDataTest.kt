package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_CHECK_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import kotlin.test.Test

class CheckDataTest {
    @Test
    fun toCsv() {
        assertThat(newCheckData(1000).toCsv())
            .isEqualTo("""
                "1234567890",1000,"check desc",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent())
    }

    @Test
    fun extractCompositeCheckData() {
        val compositeCheckData = CheckDataModel(
            ACCOUNT_NUMBER,
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
                ),
                CheckEntriesTableRow(
                    normalizeDate("4 15 2020"),
                    1001,
                    "Another Guy",
                    "desc",
                    2000.asCurrency(),
                ),
            )),
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )
        val checkData1 = CheckDataModel(
            ACCOUNT_NUMBER,
            1000,
            "Some Guy",
            "test",
            normalizeDate("4 10 2020"),
            1500.asCurrency(),
            null,
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )
        val checkData2 = CheckDataModel(
            ACCOUNT_NUMBER,
            1001,
            "Another Guy",
            "desc",
            normalizeDate("4 15 2020"),
            2000.asCurrency(),
            null,
            CHECK_BATES_STAMP,
            BASIC_CHECK_PAGE_METADATA
        )

        assertThat(compositeCheckData.extractNestedChecks()).isEqualTo(listOf(checkData1, checkData2))
    }
}