package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_BANK_STATEMENT
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE_DATE
import com.goldberg.law.document.model.ModelValues.TEST_TRANSACTION_ID
import com.goldberg.law.document.model.ModelValues.batesStampsMap
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newBasicBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.*
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.*
import com.nimbusds.jose.shaded.gson.Gson
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.math.BigDecimal

class BankStatementTest {
    private val logger = KotlinLogging.logger {}

    @ParameterizedTest
    @ValueSource(strings = [BankTypes.WF_BANK, DocumentType.CreditCardTypes.C1_CC])
    fun testNetSpendingMultipleUpdates(bankType: String) {
        val record1 = newHistoryRecord(amount = 0.0)
        val record2 = newHistoryRecord(amount = 500.0)
        val record3 = newHistoryRecord(amount = -500.0)
        val record4 = newHistoryRecord(amount = -500.0)

        var statement = newBankStatement(classification = bankType, transactions = listOf())
        assertThat(statement.getNetTransactions()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(0.asCurrency())

        statement = newBankStatement(classification = bankType, transactions = listOf(record1))
        assertThat(statement.getNetTransactions()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(0.asCurrency())

        statement = newBankStatement(classification = bankType, transactions = listOf(record1, record2))
        assertThat(statement.getNetTransactions()).isEqualTo(500.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())

        statement = newBankStatement(classification = bankType, transactions = listOf(record1, record2, record3))
        assertThat(statement.getNetTransactions()).isEqualTo(ZERO)
        assertThat(statement.getTotalSpending()).isEqualTo(500.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())

        statement = newBankStatement(classification = bankType, transactions = listOf(record1, record2, record3, record4))
        assertThat(statement.getNetTransactions()).isEqualTo(-500.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(1000.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())
    }

    @Test
    fun testNetSpendingMultipleRecordsPerPage() {
        val statement = newBankStatement(
            transactions = listOf(
                newHistoryRecord(amount = 10.0),
                newHistoryRecord(amount = 20.0),
                newHistoryRecord(amount = 40.0),
                newHistoryRecord(amount = 30.0),
                newHistoryRecord(amount = -50.0),
                newHistoryRecord(amount = -25.0),
                newHistoryRecord(amount = 10.0),
                newHistoryRecord(amount = 20.0),
                newHistoryRecord(amount = 40.0),
                newHistoryRecord(amount = 30.0),
                newHistoryRecord(amount = -50.0),
                newHistoryRecord(amount = -25.0),
                newHistoryRecord(amount = -1000.0),
        ))
        assertThat(statement.getNetTransactions()).isEqualTo((-950).asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(1150.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(200.asCurrency())
    }

    @Test
    fun testToCsv() {
        assertThat(BASIC_BANK_STATEMENT.toCsv())
            .isEqualTo("4/3/2020,\"test\",-500.00,,\"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER\",\"AG-12345-2\",4/7/2020,\"$FILENAME\",2,,")
    }

    @Test
    fun testToCsvMultiplePages() {
        val statement = newBankStatement(
            pages = setOf(2, 3),
            transactions = listOf(BASIC_TH_RECORD, newHistoryRecord(page = 3), newHistoryRecord(amount = 1500.00, page = 3))
        )
        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-2",4/7/2020,"$FILENAME",2,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                4/3/2020,"test",1500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
            """.trimIndent()
        )
    }

    @Test
    fun testToCsvMultiplePagesSorted() {
        val statement = newBankStatement(
            beginningBalance = 500.00.asCurrency(),
            endingBalance = 1000.00.asCurrency(),
            transactions = listOf(
                BASIC_TH_RECORD,
                newHistoryRecord(page = 3),
                newHistoryRecord(amount = null, page = 3),
                newHistoryRecord(date = normalizeDate("3/8 2019"), page = 3),
                newHistoryRecord(date = normalizeDate("8/3 2024"), page = 3),
                newHistoryRecord(page = 1),
                newHistoryRecord(amount = 0.0, page = 1),
                newHistoryRecord(date = normalizeDate("3/8 2019"), page = 1),
                newHistoryRecord(date = normalizeDate("8/3 2024"), page = 1)
            ),
            batesStamps = batesStampsMap(3)
        )
        // it doesn't sort by statement page number, so it does it based on which page was added to the statement first
        assertThat(statement.toCsv()).isEqualTo(
            """
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-1",4/7/2020,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-2",4/7/2020,"$FILENAME",2,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-1",4/7/2020,"$FILENAME",1,,
                4/3/2020,"test",0.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-1",4/7/2020,"$FILENAME",1,,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-1",4/7/2020,"$FILENAME",1,,
            """.trimIndent()
        )
    }

    @Test
    fun testSuspiciousStatementSingleRecordIncorrect() {
        val statement = newBankStatement(
            pages = setOf(1, 2, 3),
            accountNumber = ACCOUNT_NUMBER,
            beginningBalance = 2000.00.asCurrency(),
            endingBalance = 1000.00.asCurrency(),
            transactions = listOf(
                BASIC_TH_RECORD,
                newHistoryRecord(page = 3),
                newHistoryRecord(amount = null, page = 3)
            )
        )


        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-2",4/7/2020,"$FILENAME",2,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345-3",4/7/2020,"$FILENAME",3,,
            """.trimIndent()
        )
        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(2)
        assertThat(suspiciousReasons).anyMatch { it == BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS }
        assertThat(suspiciousReasons).anyMatch { it == TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("4/3/2020") }
    }

    @Test
    fun testSuspiciousStatementOnlyNumbersDoNotAddUp() {
        val statement = newBankStatement(
            accountNumber = ACCOUNT_NUMBER,
            beginningBalance = ZERO,
            endingBalance = 50.asCurrency(),
            transactions = listOf(
                newHistoryRecord(amount = 10.0),
                newHistoryRecord(amount = 20.0),
                newHistoryRecord(amount = 40.0),
            )
        )

        assertThat(statement.getNetTransactions()).isEqualTo(70.asCurrency())
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(1)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(0.0.asCurrency(), 70.0.asCurrency(), 50.0.asCurrency(), 50.0.asCurrency()))
    }

    @Test
    fun testSuspiciousReasonsMultiple() {
        val statement = newBankStatement(
            pages = setOf(1, 2),
            beginningBalance = ZERO,
            endingBalance = 100.asCurrency(),
            accountNumber = ACCOUNT_NUMBER,
            transactions = listOf(
                newHistoryRecord(amount = 10.0),
                newHistoryRecord(amount = 20.0),
                newHistoryRecord(amount = 40.0),
                newHistoryRecord(amount = 30.0),
                newHistoryRecord(amount = -50.0),
                newHistoryRecord(amount = -25.0),
                newHistoryRecord(amount = 0.0)
            ))
        assertThat(statement.getNetTransactions()).isEqualTo(25.asCurrency())
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        assertThat(statement.hasSuspiciousRecords()).isTrue()

        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(3)
            .contains(BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(ZERO, 25.asCurrency(), 100.asCurrency(), 100.asCurrency()))
            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("4/3/2020"))
    }

    @Test
    fun testNumbersAddUpBank() {
        val statement = newBankStatement(beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD))
        assertThat(statement.isSuspicious()).isTrue()
    }

    @Test
    fun testNumbersAddUpCreditCard() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC, beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD))
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterest() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC, beginningBalance = ZERO, endingBalance = 750.asCurrency(), transactions = listOf(BASIC_TH_RECORD), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC, beginningBalance = ZERO, endingBalance = 750.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC, beginningBalance = ZERO, endingBalance = 1000.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency(), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesChargedAlreadyIncluded() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC, beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency(), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun test() {
        val statement = BankStatement(
            pageMetadata = ClassifiedPdfMetadata(
                filename = "EagleBankx2492Stmt(2020)MH-000151-000190[1-3]",
                pages = setOf(1),
                classification = "Eagle Bank",
            ),
            date = normalizeDate("Jan 15, 2020"),
            accountNumber = "0100002492",
            beginningBalance = 5091.61.asCurrency(),
            endingBalance = 2777.13.asCurrency(),
            interestCharged = null,
            feesCharged = null,
            batesStamps = batesStampsMap(1),
            transactions = mutableListOf(
                TransactionHistoryRecord(
                    id = TEST_TRANSACTION_ID,
                    date = normalizeDate("Jan 1, 2020"),
                    description ="' POS Purchase MERCHANT PURCHASE TERMINAL 55432869 SQ *SQ *PIXIELANE ERIN gosq.com MD 12-24-19 12:00 AM XXXXXXXXXXXX6557",
                    amount = (-2314.48).asCurrency(),
                    filePageNumber = 1,
                )
            )
        )
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testToStatementSummaryNormal() {
        assertThat(BASIC_BANK_STATEMENT.toStatementSummary()).isEqualTo(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                FIXED_STATEMENT_DATE_DATE,
                500.asCurrency(),
                ZERO,
                (-500).asCurrency(),
                1,
                setOf("$BATES_STAMP-2"),
                FILENAME,
                setOf(2),
                false,
                listOf()
        ))
    }

    @Test
    fun testToStatementSummaryMultipleRecords() {
        val batesStamp2 = "AG-123457"
        val batesStamp3 = "AG-123458"
        val statement = newBankStatement(
            pages = setOf(1,2,3),
            transactions = listOf(
                BASIC_TH_RECORD,
                newHistoryRecord(amount = 1000.0, page = 2)
            ),
            beginningBalance = 500.asCurrency(),
            endingBalance = 1000.asCurrency(),
            batesStamps = mapOf(2 to batesStamp2, 3 to batesStamp3, 1 to BATES_STAMP)

        )
        assertThat(statement.toStatementSummary()).isEqualTo(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                FIXED_STATEMENT_DATE_DATE,
                500.asCurrency(),
                1000.asCurrency(),
                500.asCurrency(),
                2,
                setOf(BATES_STAMP, batesStamp2, batesStamp3),
                FILENAME,
                setOf(1,2, 3),
                false,
                listOf()
            ))
    }

    @Test
    fun testDeserializeJacksonAndGson() {
        val statement = BankStatement(
            pageMetadata = ClassifiedPdfMetadata(
                filename =  "CC - Rob - Wells Fargo Visa x2582 Stmt (2022.04.06) 001299-001302",
                pages = setOf(1),
                classification =  "WF CC",
            ),
            date =  normalizeDate("4 6 2022"),
            accountNumber =  "2582",
            beginningBalance = BigDecimal(0.0).setScale(1),
            endingBalance = BigDecimal(0.0).setScale(1),
            interestCharged = BigDecimal(0.0).setScale(1),
            feesCharged = BigDecimal(0.0).setScale(1),
            transactions = listOf(),
            batesStamps = mapOf(1 to "AG-12345"),
            checks = mutableMapOf(),
        )

        val statementString = "{\"pageMetadata\":{\"filename\":\"CC - Rob - Wells Fargo Visa x2582 Stmt (2022.04.06) 001299-001302\",\"pages\":[1],\"classification\":\"WF CC\"},\"date\":\"4/6/2022\",\"accountNumber\":\"2582\",\"beginningBalance\":0.0,\"endingBalance\":0.0,\"interestCharged\":0.0,\"feesCharged\":0.0,\"transactions\":[],\"batesStamps\":{\"1\":\"AG-12345\"},\"checks\":{},\"netTransactions\":-500.00,\"totalSpending\":500.00,\"totalIncomeCredits\":0.00,\"suspiciousReasons\":[\"Beginning balance (0.00) + net transactions (-500.00) != ending balance (0.00). Expected (0.00)\",\"Found transactions with dates outside of this statement: [4/3/2020]\"]}\n"

        val statementJackson = OBJECT_MAPPER.readValue(statementString, BankStatement::class.java)

        val statementGson = Gson().fromJson(statementString, BankStatement::class.java)

        // numbers don't serialize to 2 digits... TODO: is this a problem?
        assertThat(statementJackson)
            .isEqualTo(statementGson)
            .isEqualTo(statement)

        assertThat(statementJackson.getSuspiciousReasons()).contains(BankStatement.SuspiciousReasons.NO_TRANSACTIONS_FOUND)
    }

    @Test
    fun testSerializeAndDeserializeJacksonAndGson() {
        val statement = newBankStatement()

        val statementJackson = OBJECT_MAPPER.readValue(statement.toStringDetailed(), BankStatement::class.java)

        val statementGson = Gson().fromJson(Gson().toJson(statement), BankStatement::class.java)

        assertThat(statementJackson).isEqualTo(statementGson).isEqualTo(statement)
    }

    @Test
    fun testSerializeAndDeserializeJacksonAndGsonWithChecks() {
        val statement = newBankStatement().copy(checks = mutableMapOf(1000 to ClassifiedPdfMetadata("testCheck", 2, DocumentType.CheckTypes.MISC_CHECK)))

        val statementJackson = OBJECT_MAPPER.readValue(statement.toStringDetailed(), BankStatement::class.java)

        val statementGson = Gson().fromJson(Gson().toJson(statement), BankStatement::class.java)

        assertThat(statementJackson).isEqualTo(statementGson).isEqualTo(statement)
    }

    @Test
    fun testMd5HashAfterSerializeAndDeserializeJacksonAndGson() {
        val statement = newBankStatement(
            feesCharged = 50.asCurrency(),
            interestCharged = 100.asCurrency(),
            transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(1000))),
        )

        val equalStatement = newBankStatement(
            feesCharged = 50.asCurrency(),
            interestCharged = 100.asCurrency(),
            transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(1000))),
        )

        val originalHash = statement.md5Hash()
        assertThat(originalHash).isEqualTo(equalStatement.md5Hash())

        val statementJackson = OBJECT_MAPPER.readValue(statement.toStringDetailed(), BankStatement::class.java)

        val statementGson = Gson().fromJson(Gson().toJson(statement), BankStatement::class.java)

        assertThat(statementJackson).isEqualTo(statementGson).isEqualTo(statement)
        // TODO: apparently GSON is able to construct the instance without the date, because statementDate is null even though it's derived from the
        // date on instantiation of the class, so this isn't working properly
        assertThat(statementJackson.toStringDetailed()).isNotEqualTo(statementGson.toStringDetailed())
        assertThat(originalHash).isEqualTo(statementJackson.md5Hash()).isNotEqualTo(statementGson.md5Hash())
    }

    @Test
    fun testAzureFileName() {
        assertThat(newBasicBankStatement().azureFileName())
            .isEqualTo("1234567890:WF Bank:4_7_2020.json")

        assertThat(newBankStatement(pages = setOf(2,3,7)).azureFileName())
            .isEqualTo("1234567890:WF Bank:4_7_2020.json")

        assertThat(newBankStatement(statementDate = null).azureFileName())
            .isEqualTo("1234567890:WF Bank:null:test.pdf[2-2].json")

        assertThat(newBankStatement(accountNumber = null).azureFileName())
            .isEqualTo("null:WF Bank:4_7_2020:test.pdf[2-2].json")

        assertThat(newBankStatement(statementDate = null, accountNumber = null).azureFileName())
            .isEqualTo("null:WF Bank:null:test.pdf[2-2].json")

        assertThat(newBankStatement(accountNumber = null, statementDate = null, pages = setOf(2,3,7)).azureFileName()).isEqualTo("null:WF Bank:null:test.pdf[2-7].json")
    }
}