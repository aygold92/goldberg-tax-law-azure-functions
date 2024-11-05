package com.goldberg.law.util

import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.DocumentField
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.function.model.InputFileMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource

class KotlinExtensionsTest {
    @ParameterizedTest
    @ValueSource(strings = ["50.0", "50.00", "50", "$50", "$50.00", "$50.0", "+$50.00"])
    fun testParseCurrencyPositive(value: String) {
        assertThat(value.parseCurrency()).isEqualTo(50.0.asCurrency())
    }

    @ParameterizedTest
    @ValueSource(strings = ["-50.0", "-50.00", "-50", "-$50", "-$50.00", "-$50.0", " - 50.0 * "])
    fun testParseCurrencyNegative(value: String) {
        assertThat(value.parseCurrency()).isEqualTo((-50.0).asCurrency())
    }

    @Test
    fun testParseCurrencyOffBy01() {
        val valueString = "{\"type\":\"number\",\"content\":\"201,689.60\",\"value\":201689.59375}"
        val field = OBJECT_MAPPER.readValue(valueString, DocumentField::class.java)

        assertThat(field.currencyValue()).isEqualTo(201689.6.asCurrency())
    }

    @Test
    fun testGetFileName() {
        assertThat("./testInput/nonexistent/12345.asdknalksdn/test.pdf".getDocumentName()).isEqualTo("test")
    }

    @Test
    fun testToStringDetailed() {
        val valueString = "{\"docType\":\"BankCreditCardExtractor_V5\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":0.0,\"x\":0.0},{\"y\":0.0,\"x\":8.5},{\"y\":11.0,\"x\":8.5},{\"y\":11.0,\"x\":0.0}]}],\"spans\":[{\"offset\":0,\"length\":829}],\"fields\":{\"BatesStamp\":{\"value\":\"MH-000152\",\"type\":\"string\",\"content\":\"MH-000152\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":10.5202,\"x\":6.7713},{\"y\":10.5226,\"x\":7.4976},{\"y\":10.661,\"x\":7.4972},{\"y\":10.6586,\"x\":6.7709}]}],\"spans\":[{\"offset\":807,\"length\":9}],\"confidence\":0.952},\"TransactionTableCredits\":{\"value\":[{\"value\":{\"Additions\":{\"value\":50.0,\"type\":\"number\",\"content\":\"50.00\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.1105,\"x\":7.0435},{\"y\":3.1081,\"x\":7.4017},{\"y\":3.237,\"x\":7.4026},{\"y\":3.2394,\"x\":7.0443}]}],\"spans\":[{\"offset\":327,\"length\":5}],\"confidence\":0.821},\"Description\":{\"value\":\"' Preauthorized Credit VENMO CASHOUT 191216 2853837810\",\"type\":\"string\",\"content\":\"' Preauthorized Credit\\nVENMO CASHOUT 191216 2853837810\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.1033,\"x\":2.3303},{\"y\":3.0977,\"x\":3.9008},{\"y\":3.5329,\"x\":3.9023},{\"y\":3.5385,\"x\":2.3318}]}],\"spans\":[{\"offset\":266,\"length\":22},{\"offset\":295,\"length\":31}],\"confidence\":0.657},\"Date\":{\"value\":\"12-16\",\"type\":\"string\",\"content\":\"12-16\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.1033,\"x\":1.8576},{\"y\":3.1033,\"x\":2.2205},{\"y\":3.2418,\"x\":2.2205},{\"y\":3.2418,\"x\":1.8576}]}],\"spans\":[{\"offset\":260,\"length\":5}],\"confidence\":0.837}},\"type\":\"object\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.375},{\"value\":{\"Additions\":{\"value\":1200.0,\"type\":\"number\",\"content\":\"1,200.00\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.5497,\"x\":6.8334},{\"y\":3.5521,\"x\":7.4071},{\"y\":3.6953,\"x\":7.4065},{\"y\":3.6929,\"x\":6.8328}]}],\"spans\":[{\"offset\":341,\"length\":8}],\"confidence\":0.743},\"Description\":{\"value\":\"Deposit\",\"type\":\"string\",\"content\":\"Deposit\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.5425,\"x\":2.4138},{\"y\":3.5354,\"x\":2.9129},{\"y\":3.712,\"x\":2.9154},{\"y\":3.7192,\"x\":2.4163}]}],\"spans\":[{\"offset\":333,\"length\":7}],\"confidence\":0.56},\"Date\":{\"value\":\"01-03\",\"type\":\"string\",\"content\":\"01-03\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.533,\"x\":1.8337},{\"y\":3.533,\"x\":2.2444},{\"y\":3.6953,\"x\":2.2444},{\"y\":3.6953,\"x\":1.8337}]}],\"spans\":[{\"offset\":289,\"length\":5}],\"confidence\":0.668}},\"type\":\"object\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.314},{\"value\":{\"Additions\":{\"value\":1000.0,\"type\":\"number\",\"content\":\"1,000.00\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.7048,\"x\":6.8334},{\"y\":3.7049,\"x\":7.3969},{\"y\":3.8433,\"x\":7.3969},{\"y\":3.8433,\"x\":6.8334}]}],\"spans\":[{\"offset\":416,\"length\":8}],\"confidence\":0.771},\"Description\":{\"value\":\"' Transfer Credit TRANSFER FROM DEPOSIT ACCOUNT XXXXXXX1767\",\"type\":\"string\",\"content\":\"' Transfer Credit TRANSFER FROM DEPOSIT ACCOUNT XXXXXXX1767\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.7049,\"x\":2.3256},{\"y\":3.7049,\"x\":5.2576},{\"y\":3.9913,\"x\":5.2576},{\"y\":3.9913,\"x\":2.3256}]}],\"spans\":[{\"offset\":356,\"length\":59}],\"confidence\":0.663},\"Date\":{\"value\":\"01-03\",\"type\":\"string\",\"content\":\"01-03\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":3.6977,\"x\":1.8385},{\"y\":3.7001,\"x\":2.2357},{\"y\":3.8433,\"x\":2.2348},{\"y\":3.8409,\"x\":1.8376}]}],\"spans\":[{\"offset\":350,\"length\":5}],\"confidence\":0.756}},\"type\":\"object\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.415}],\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.646},\"ChecksTable\":{\"value\":null,\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.962},\"EndingBalance\":{\"value\":null,\"type\":\"number\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.938},\"Page\":{\"value\":\"2\",\"type\":\"string\",\"content\":\"2\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":1.4514,\"x\":7.034},{\"y\":1.4514,\"x\":7.1152},{\"y\":1.6042,\"x\":7.1152},{\"y\":1.6042,\"x\":7.034}]}],\"spans\":[{\"offset\":43,\"length\":1}],\"confidence\":0.755},\"TotalPages\":{\"value\":\"2\",\"type\":\"string\",\"content\":\"2\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":1.4514,\"x\":7.3444},{\"y\":1.4514,\"x\":7.416},{\"y\":1.6042,\"x\":7.416},{\"y\":1.6042,\"x\":7.3444}]}],\"spans\":[{\"offset\":48,\"length\":1}],\"confidence\":0.719},\"TransactionTableAmount\":{\"value\":null,\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.59},\"StatementDate\":{\"value\":\"January 15, 2020\",\"type\":\"string\",\"content\":\"January 15, 2020\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":1.5946,\"x\":1.8385},{\"y\":1.5946,\"x\":2.9941},{\"y\":1.7569,\"x\":2.9941},{\"y\":1.7569,\"x\":1.8385}]}],\"spans\":[{\"offset\":21,\"length\":16}],\"confidence\":0.904},\"FeesCharged\":{\"value\":null,\"type\":\"number\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.909},\"AccountNumber\":{\"value\":\"0100002492\",\"type\":\"string\",\"content\":\"0100002492\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":1.5994,\"x\":6.5517},{\"y\":1.5994,\"x\":7.4303},{\"y\":1.7474,\"x\":7.4303},{\"y\":1.7474,\"x\":6.5517}]}],\"spans\":[{\"offset\":50,\"length\":10}],\"confidence\":0.943},\"Page/Total\":{\"value\":null,\"type\":\"string\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.971},\"InterestCharged\":{\"value\":null,\"type\":\"number\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.908},\"TransactionTableDepositWithdrawal\":{\"value\":null,\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.949},\"TransactionTableCreditsCharges\":{\"value\":null,\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.921},\"SummaryOfAccounts\":{\"value\":null,\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.987},\"BankIdentifier\":{\"value\":null,\"type\":\"string\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.646},\"TransactionTableDebits\":{\"value\":[{\"value\":{\"Description\":{\"value\":\"' ATM Surcharge SURCHARGE AMOUNT TERMINAL LK435949 13805 BLAIRS VALLE Y RD. MERCERBUR PA01-02-20 11:25 AM XXXXXXXXXXXX6557\",\"type\":\"string\",\"content\":\"' ATM Surcharge SURCHARGE AMOUNT TERMINAL LK435949 13805 BLAIRS VALLE Y RD. MERCERBUR PA01-02-20 11:25 AM XXXXXXXXXXXX6557\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":2.053,\"x\":2.3303},{\"y\":2.053,\"x\":5.9691},{\"y\":2.4922,\"x\":5.9691},{\"y\":2.4922,\"x\":2.3303}]}],\"spans\":[{\"offset\":97,\"length\":122}],\"confidence\":0.666},\"Subtractions\":{\"value\":4.0,\"type\":\"number\",\"content\":\"4.00\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":2.0434,\"x\":7.1295},{\"y\":2.0386,\"x\":7.4182},{\"y\":2.1914,\"x\":7.4208},{\"y\":2.1962,\"x\":7.1321}]}],\"spans\":[{\"offset\":220,\"length\":4}],\"confidence\":0.823},\"Date\":{\"value\":\"01-02\",\"type\":\"string\",\"content\":\"01-02\",\"boundingRegions\":[{\"pageNumber\":1,\"boundingPolygon\":[{\"y\":2.0506,\"x\":1.8337},{\"y\":2.053,\"x\":2.2405},{\"y\":2.1962,\"x\":2.2396},{\"y\":2.1938,\"x\":1.8329}]}],\"spans\":[{\"offset\":91,\"length\":5}],\"confidence\":0.754}},\"type\":\"object\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.822}],\"type\":\"array\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.774},\"BeginningBalance\":{\"value\":null,\"type\":\"number\",\"content\":null,\"boundingRegions\":null,\"spans\":null,\"confidence\":0.934}},\"confidence\":0.964}"
        val value = OBJECT_MAPPER.readValue(valueString, AnalyzedDocument::class.java)

        val result = value.toStringDetailed().also { println(it) }
        val new = OBJECT_MAPPER.readValue(result, AnalyzedDocument::class.java)

        val expectedString = "{\"docType\":\"BankCreditCardExtractor_V5\",\"fields\":{\"BatesStamp\":{\"value\":\"MH-000152\",\"type\":\"string\",\"content\":\"MH-000152\"},\"TransactionTableCredits\":{\"value\":[{\"value\":{\"Additions\":{\"value\":50.0,\"type\":\"number\",\"content\":\"50.00\"},\"Description\":{\"value\":\"' Preauthorized Credit VENMO CASHOUT 191216 2853837810\",\"type\":\"string\",\"content\":\"' Preauthorized Credit\\nVENMO CASHOUT 191216 2853837810\"},\"Date\":{\"value\":\"12-16\",\"type\":\"string\",\"content\":\"12-16\"}},\"type\":\"object\",\"content\":null},{\"value\":{\"Additions\":{\"value\":1200.0,\"type\":\"number\",\"content\":\"1,200.00\"},\"Description\":{\"value\":\"Deposit\",\"type\":\"string\",\"content\":\"Deposit\"},\"Date\":{\"value\":\"01-03\",\"type\":\"string\",\"content\":\"01-03\"}},\"type\":\"object\",\"content\":null},{\"value\":{\"Additions\":{\"value\":1000.0,\"type\":\"number\",\"content\":\"1,000.00\"},\"Description\":{\"value\":\"' Transfer Credit TRANSFER FROM DEPOSIT ACCOUNT XXXXXXX1767\",\"type\":\"string\",\"content\":\"' Transfer Credit TRANSFER FROM DEPOSIT ACCOUNT XXXXXXX1767\"},\"Date\":{\"value\":\"01-03\",\"type\":\"string\",\"content\":\"01-03\"}},\"type\":\"object\",\"content\":null}],\"type\":\"array\",\"content\":null},\"ChecksTable\":{\"value\":null,\"type\":\"array\",\"content\":null},\"EndingBalance\":{\"value\":null,\"type\":\"number\",\"content\":null},\"Page\":{\"value\":\"2\",\"type\":\"string\",\"content\":\"2\"},\"TotalPages\":{\"value\":\"2\",\"type\":\"string\",\"content\":\"2\"},\"TransactionTableAmount\":{\"value\":null,\"type\":\"array\",\"content\":null},\"StatementDate\":{\"value\":\"January 15, 2020\",\"type\":\"string\",\"content\":\"January 15, 2020\"},\"FeesCharged\":{\"value\":null,\"type\":\"number\",\"content\":null},\"AccountNumber\":{\"value\":\"0100002492\",\"type\":\"string\",\"content\":\"0100002492\"},\"Page/Total\":{\"value\":null,\"type\":\"string\",\"content\":null},\"InterestCharged\":{\"value\":null,\"type\":\"number\",\"content\":null},\"TransactionTableDepositWithdrawal\":{\"value\":null,\"type\":\"array\",\"content\":null},\"TransactionTableCreditsCharges\":{\"value\":null,\"type\":\"array\",\"content\":null},\"SummaryOfAccounts\":{\"value\":null,\"type\":\"array\",\"content\":null},\"BankIdentifier\":{\"value\":null,\"type\":\"string\",\"content\":null},\"TransactionTableDebits\":{\"value\":[{\"value\":{\"Description\":{\"value\":\"' ATM Surcharge SURCHARGE AMOUNT TERMINAL LK435949 13805 BLAIRS VALLE Y RD. MERCERBUR PA01-02-20 11:25 AM XXXXXXXXXXXX6557\",\"type\":\"string\",\"content\":\"' ATM Surcharge SURCHARGE AMOUNT TERMINAL LK435949 13805 BLAIRS VALLE Y RD. MERCERBUR PA01-02-20 11:25 AM XXXXXXXXXXXX6557\"},\"Subtractions\":{\"value\":4.0,\"type\":\"number\",\"content\":\"4.00\"},\"Date\":{\"value\":\"01-02\",\"type\":\"string\",\"content\":\"01-02\"}},\"type\":\"object\",\"content\":null}],\"type\":\"array\",\"content\":null},\"BeginningBalance\":{\"value\":null,\"type\":\"number\",\"content\":null}}}"
        val expected = OBJECT_MAPPER.readValue(expectedString, AnalyzedDocument::class.java)

        new.fields.forEach { fieldEntry ->
            assertThat(fieldEntry.value.value).isEqualTo(expected.fields[fieldEntry.key]?.value)
        }
    }

    @Test
    fun testToMap() {
        assertThat(InputFileMetadata(true, true, listOf("Test", "test2")).toMap())
            .isEqualTo(mapOf("split" to "true", "analyzed" to "true", "statements" to "[Test, test2]"))
    }

    @Test
    fun testContentAndValueDifferent() {
        val value = "{\"type\":\"number\",\"content\":\"-18\",\"value\":-2906.179931640625}"
        val field = OBJECT_MAPPER.readValue(value, DocumentField::class.java)
        assertThat(field.currencyValue()).isEqualTo((-2906.18).asCurrency())
    }

    @Test
    fun testDocumentFieldToCurrencyMultipleContentValues() {
        val content = "$58.38 - $120"
        val valueString = "{\"type\":\"number\",\"content\":\"$content\",\"value\":58.38}"
        val field = OBJECT_MAPPER.readValue(valueString, DocumentField::class.java)
        assertThat(field.currencyValue()).isEqualTo(58.38.asCurrency())
    }

    @ParameterizedTest
    @ValueSource(strings = ["-2906.18", "- $2,906.18", " - $ 2,906.18"])
    fun testDocumentFieldToCurrencyNegativeValue(contentValue: String) {
        val value = "{\"type\":\"number\",\"content\":\"$contentValue\",\"value\":2906.179931640625}"
        val field = OBJECT_MAPPER.readValue(value, DocumentField::class.java)
        assertThat(field.currencyValue()).isEqualTo(-2906.18.asCurrency())
    }

    @ParameterizedTest
    @ValueSource(strings = [
        "{\"type\":\"number\",\"content\":\"- \$2,906.18\",\"value\":2906.179931640625}",
        "{\"type\":\"number\",\"content\":\" - \$2,906.18\",\"value\":2906.179931640625}",
        "{\"type\":\"number\",\"content\":\"-18\",\"value\":2906.179931640625}",
        "{\"type\":\"number\",\"content\":\"-2,906.18\",\"value\":-2906.179931640625}",
    ])
    fun testDocumentFieldToPositiveCurrencyValue(value: String) {
        val field = OBJECT_MAPPER.readValue(value, DocumentField::class.java)
        assertThat(field.positiveCurrencyValue()).isEqualTo(2906.18.asCurrency())
    }

    @Test
    fun testDocumentFieldToCurrencyNormal() {
        val value = "{\"type\":\"number\",\"content\":\"$2,906.18\",\"value\":2906.179931640625}"
        val field = OBJECT_MAPPER.readValue(value, DocumentField::class.java)
        assertThat(field.currencyValue()).isEqualTo(2906.18.asCurrency())
    }

    @ParameterizedTest
    @ValueSource(ints = [1,2,3,4,5,6,7,8])
    fun testBreakIntoGroups(numGroups: Int) {
        val list = listOf("A", "B", "C", "D", "E", "F", "G", "H")

        val group = list.breakIntoGroups(numGroups)
        assertThat(group).hasSize(numGroups)
        group.values.forEach {
            assertThat(it).hasSizeLessThanOrEqualTo((8/numGroups + 1)).hasSizeGreaterThanOrEqualTo(8/numGroups)
        }
        assertThat(group.flatMap { it.value }).hasSameElementsAs(list).hasSize(list.size)
    }

    @Test
    fun testBreakIntoGroupsOneGroup() {
        val list = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val group1 = list.breakIntoGroups(1)
        assertThat(group1).hasSize(1)
        assertThat(group1.values.first()).hasSize(list.size).hasSameElementsAs(list)
    }

    @Test
    fun testBreakIntoGroupsOnePerGroup() {
        val list = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val group8 = list.breakIntoGroups(8)
        assertThat(group8).hasSize(8)
        group8.forEach {
            assertThat(it.value).hasSize(1)
        }
        assertThat(group8.flatMap { it.value }).hasSameElementsAs(list).hasSize(list.size)
    }

    @Test
    fun breakIntoGroupsLarger() {
        val list = listOf("A", "B", "C", "D", "E", "F", "G", "H")
        val group = list.breakIntoGroups(0)
        assertThat(group).hasSize(1)
        assertThat(group[0]).hasSize(list.size)
        assertThat(group.flatMap { it.value }).hasSameElementsAs(list).hasSize(list.size)
    }

    @Test
    fun testMapAsync() {
        assertThat(listOf("A", "B", "C", "D", "E", "F", "G", "H").mapAsync {
            it + it
        }).isEqualTo(
            listOf("AA", "BB", "CC", "DD", "EE", "FF", "GG", "HH")
        )
    }

    companion object {
        val OBJECT_MAPPER = ObjectMapper()
    }
}