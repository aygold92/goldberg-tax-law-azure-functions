package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_BANK_STATEMENT
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newBasicBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.ModelValues.newTransactionPage
import com.goldberg.law.document.model.input.StatementDataModel.Keys.ENDING_BALANCE
import com.goldberg.law.document.model.output.*
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
        val statement = newBankStatement(classification = bankType)
        assertThat(statement.getNetTransactions()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(0.asCurrency())

        statement.update(transactions = listOf(newHistoryRecord(amount = 0.0, pageMetadata = newTransactionPage(1))), pageMetadata = newTransactionPage(1))
        assertThat(statement.getNetTransactions()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(0.asCurrency())

        statement.update(transactions = listOf(newHistoryRecord(amount = 500.0, pageMetadata = newTransactionPage(2))), pageMetadata = newTransactionPage(2))
        assertThat(statement.getNetTransactions()).isEqualTo(500.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(0.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())

        statement.update(transactions = listOf(newHistoryRecord(amount = -500.0, pageMetadata = newTransactionPage(3))), pageMetadata = newTransactionPage(3))
        assertThat(statement.getNetTransactions()).isEqualTo(ZERO)
        assertThat(statement.getTotalSpending()).isEqualTo(500.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())

        statement.update(transactions = listOf(newHistoryRecord(amount = -500.0, pageMetadata = newTransactionPage(4))), pageMetadata = newTransactionPage(4))
        assertThat(statement.getNetTransactions()).isEqualTo(-500.asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(1000.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(500.asCurrency())

        assertThat(statement.getPageRange()).isEqualTo(Pair(1, 4))
    }

    @Test
    fun testNetSpendingMultipleRecordsPerPage() {
        val statement = newBankStatement()
        val page1 = newTransactionPage(1)
        // statement.update(thPage = newTransactionPage(1, 10.0, 20.0, 40.0, 30.0, -50.0, -25.0))
        statement.update(transactions = listOf(
            newHistoryRecord(amount = 10.0, pageMetadata = page1),
            newHistoryRecord(amount = 20.0, pageMetadata = page1),
            newHistoryRecord(amount = 40.0, pageMetadata = page1),
            newHistoryRecord(amount = 30.0, pageMetadata = page1),
            newHistoryRecord(amount = -50.0, pageMetadata = page1),
            newHistoryRecord(amount = -25.0, pageMetadata = page1),
        ))
        assertThat(statement.getNetTransactions()).isEqualTo(25.asCurrency())
        val page2 = newTransactionPage(2)
        statement.update(transactions = listOf(
            newHistoryRecord(amount = 10.0, pageMetadata = page1),
            newHistoryRecord(amount = 20.0, pageMetadata = page1),
            newHistoryRecord(amount = 40.0, pageMetadata = page1),
            newHistoryRecord(amount = 30.0, pageMetadata = page1),
            newHistoryRecord(amount = -50.0, pageMetadata = page1),
            newHistoryRecord(amount = -25.0, pageMetadata = page1),
            newHistoryRecord(amount = -1000.0, pageMetadata = page1),
        ))
        assertThat(statement.getNetTransactions()).isEqualTo((-950).asCurrency())
        assertThat(statement.getTotalSpending()).isEqualTo(1150.asCurrency())
        assertThat(statement.getTotalIncomeCredits()).isEqualTo(200.asCurrency())
    }

    @Test
    fun testToCsv() {
        assertThat(BASIC_BANK_STATEMENT.toCsv())
            .isEqualTo("4/3/2020,\"test\",-500.00,,\"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER\",\"AG-12345\",4/7/2020,2,\"$FILENAME\",1,,")
    }

    @Test
    fun testToCsvMultiplePages() {
        val otherPage = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 3)
        val statement = BASIC_BANK_STATEMENT.copy()
            .update(transactions = listOf(
                newHistoryRecord(pageMetadata = otherPage),
                newHistoryRecord(amount = 1500.00, pageMetadata = otherPage)
            ))
        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
                4/3/2020,"test",1500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
            """.trimIndent()
        )
    }

    @Test
    fun testToCsvMultiplePagesSorted() {
        val pageBefore = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 1)
        val pageBeforeTransactions = listOf(
            newHistoryRecord(pageMetadata = pageBefore),
            newHistoryRecord(amount = 0.0, pageMetadata = pageBefore),
            newHistoryRecord(date = normalizeDate("3/8 2019"), pageMetadata = pageBefore),
            newHistoryRecord(date = normalizeDate("8/3 2024"), pageMetadata = pageBefore)
        )

        val pageAfter = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 3)
        val pageAfterTransactions = listOf(
            newHistoryRecord(pageMetadata = pageAfter),
            newHistoryRecord(amount = null, pageMetadata = pageAfter),
            newHistoryRecord(date = normalizeDate("3/8 2019"), pageMetadata = pageAfter),
            newHistoryRecord(date = normalizeDate("8/3 2024"), pageMetadata = pageAfter)
        )

        val statement = newBankStatement().update(
            totalPages = 5,
            beginningBalance = 500.00.asCurrency(),
            endingBalance = 1000.00.asCurrency(),
        ).update(transactions = listOf(BASIC_TH_RECORD))
            .update(transactions = pageAfterTransactions)
            .update(transactions = pageBeforeTransactions)
        // it doesn't sort by statement page number, so it does it based on which page was added to the statement first
        assertThat(statement.toCsv()).isEqualTo(
            """
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,,
                4/3/2020,"test",0.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,,
            """.trimIndent()
        )
    }

    @Test
    fun testSuspiciousStatementSingleRecordIncorrect() {
        val page3 = newTransactionPage(3)
        val statement = newBankStatement().update(
            accountNumber = ACCOUNT_NUMBER,
            totalPages = 5,
            beginningBalance = 2000.00.asCurrency(),
            endingBalance = 1000.00.asCurrency(),
            transactions = listOf(BASIC_TH_RECORD)
        ).update(transactions = listOf(
            newHistoryRecord(pageMetadata = page3),
            newHistoryRecord(amount = null, pageMetadata = page3))
        )

        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",3,,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",3,,
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
        val page1 = newTransactionPage(1)
        val statement = newBankStatement().update(
            accountNumber = ACCOUNT_NUMBER,
            beginningBalance = ZERO,
            endingBalance = 50.asCurrency(),
        ).update(transactions = listOf(
            newHistoryRecord(amount = 10.0, pageMetadata = page1),
            newHistoryRecord(amount = 20.0, pageMetadata = page1),
            newHistoryRecord(amount = 40.0, pageMetadata = page1),
        ))

        assertThat(statement.getNetTransactions()).isEqualTo(70.asCurrency())
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(1)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(0.0, 70.0, 50.0, 50.0))
    }

    @Test
    fun testSuspiciousReasonsMultiple() {
        val page1 = newTransactionPage(1)
        val page2 = newTransactionPage(2)
        val statement = newBankStatement().update(
            beginningBalance = ZERO,
            endingBalance = 50.asCurrency(),
            accountNumber = ACCOUNT_NUMBER
        )
            .update(beginningBalance = ZERO, endingBalance = 100.asCurrency())
            .update(transactions = listOf(
                newHistoryRecord(amount = 10.0, pageMetadata = page1),
                newHistoryRecord(amount = 20.0, pageMetadata = page1),
                newHistoryRecord(amount = 40.0, pageMetadata = page1),
                newHistoryRecord(amount = 30.0, pageMetadata = page1),
                newHistoryRecord(amount = -50.0, pageMetadata = page1),
                newHistoryRecord(amount = -25.0, pageMetadata = page1),
            ))
            .update(transactions = listOf(newHistoryRecord(amount = 0.0, pageMetadata = page2)))
        assertThat(statement.getNetTransactions()).isEqualTo(25.asCurrency())
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        assertThat(statement.hasSuspiciousRecords()).isTrue()

        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(4)
            .contains(BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(ZERO, 25.asCurrency(), 100.asCurrency(), 100.asCurrency()))
            .contains(BankStatement.SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(ENDING_BALANCE, 50.asCurrency(), 100.asCurrency()))
            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("4/3/2020"))
    }

    @Test
    fun testNumbersAddUpBank() {
        val statement = newBankStatement()
            .update(beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD))
        assertThat(statement.isSuspicious()).isTrue()
    }

    @Test
    fun testNumbersAddUpCreditCard() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD))
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterest() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = ZERO, endingBalance = 750.asCurrency(), transactions = listOf(BASIC_TH_RECORD), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = ZERO, endingBalance = 750.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = ZERO, endingBalance = 1000.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency(), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesChargedAlreadyIncluded() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = ZERO, endingBalance = 500.asCurrency(), transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.asCurrency(), interestCharged = 250.asCurrency())
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testMultipleValuesStatementType() {
        val statement = BankStatement(FILENAME, BankTypes.WF_BANK, FIXED_STATEMENT_DATE, ACCOUNT_NUMBER)
            .update(documentType = DocumentType.CREDIT_CARD)

        assertThat(statement.statementType).isEqualTo(DocumentType.MIXED)
    }

    @Test
    fun testMultipleValuesStatementTypeMixed() {
        val statement = BankStatement(FILENAME, BankTypes.WF_BANK, FIXED_STATEMENT_DATE, ACCOUNT_NUMBER)
            .update(documentType = DocumentType.MIXED)

        assertThat(statement.statementType).isEqualTo(DocumentType.MIXED)
    }

    @Test
    fun test() {
        val statement = BankStatement(
            filename = "EagleBankx2492Stmt(2020)MH-000151-000190[1-3]",
                    classification = "Eagle Bank",
                    date = normalizeDate("Jan 15, 2020"),
                    accountNumber = "0100002492",
                    bankIdentifier = null,
                    startPage = 1,
                    totalPages = 2,
                    beginningBalance = 5091.61.asCurrency(),
                    endingBalance = 2777.13.asCurrency(),
                    interestCharged = null,
                    feesCharged = null,
            transactions = mutableListOf(
                TransactionHistoryRecord(
                    date = normalizeDate("Jan 1, 2020"),
                    description ="' POS Purchase MERCHANT PURCHASE TERMINAL 55432869 SQ *SQ *PIXIELANE ERIN gosq.com MD 12-24-19 12:00 AM XXXXXXXXXXXX6557",
                    amount = (-2314.48).asCurrency(),
                    pageMetadata = TransactionHistoryPageMetadata(
                        filename = "EagleBankx2492Stmt(2020)MH-000151-000190[1-3]",
                        filePageNumber = 1,
                        batesStamp = "MH100015NT",
                        date = normalizeDate("Jan 15, 2020"),
                        statementPageNum = 1
                    )
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
                setOf(BATES_STAMP),
                FILENAME,
                setOf(1),
                false,
                listOf()
        ))
    }

    @Test
    fun testToStatementSummaryMultipleRecords() {
        val batesStamp2 = "AG-123457"
        val batesStamp3 = "AG-123458"
        val statement = newBankStatement().update(
            transactions = mutableListOf(
                BASIC_TH_RECORD,
                newHistoryRecord(amount = 1000.0, pageMetadata = newTransactionPage(2, batesStamp = batesStamp2))
            ),
            totalPages = 5,
            beginningBalance = 500.asCurrency(),
            endingBalance = 1000.asCurrency()
        )
            .update(pageMetadata = newTransactionPage(1))
            .update(pageMetadata = newTransactionPage(2, batesStamp = batesStamp2))
            .update(pageMetadata = newTransactionPage(3, batesStamp = batesStamp3))
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
        val statementString = "{\"filename\":\"CC - Rob - Wells Fargo Visa x2582 Stmt (2022.04.06) 001299-001302\",\"classification\":\"WF CC\",\"date\":\"4/6/2022\",\"accountNumber\":\"2582\",\"bankIdentifier\":null,\"startPage\":1,\"totalPages\":3,\"beginningBalance\":0.0,\"endingBalance\":0.0,\"interestCharged\":0.0,\"feesCharged\":0.0,\"transactions\":[],\"statementType\":\"CREDIT_CARD\",\"primaryKey\":{\"statementDate\":\"4/6/2022\",\"accountNumber\":\"2582\",\"complete\":true},\"netTransactions\":0.0,\"suspiciousReasons\":[\"No transactions recorded\"],\"suspicious\":true,\"transactionDatesOutsideOfStatement\":[],\"pages\":[{\"filename\":\"test.pdf\",\"filePageNumber\":1,\"batesStamp\":\"AG-12345\",\"statementPageNum\":1,\"date\":\"4/6/2022\"}]}"

        val statementJackson = OBJECT_MAPPER.readValue(statementString, BankStatement::class.java)

        val statementGson = Gson().fromJson(statementString, BankStatement::class.java)

        // numbers don't serialize to 2 digits... TODO: is this a problem?
        assertThat(statementJackson)
            .isEqualTo(statementGson)
            .isEqualTo(BankStatement(
                filename =  "CC - Rob - Wells Fargo Visa x2582 Stmt (2022.04.06) 001299-001302",
                classification =  "WF CC",
                date =  normalizeDate("4 6 2022"),
                accountNumber =  "2582",
                bankIdentifier =  null,
                startPage =  1,
                totalPages =  3,
                beginningBalance = BigDecimal(0.0).setScale(1),
                endingBalance = BigDecimal(0.0).setScale(1),
                interestCharged = BigDecimal(0.0).setScale(1),
                feesCharged = BigDecimal(0.0).setScale(1),
                transactions = mutableListOf(),
                pages = mutableSetOf(newTransactionPage(1, statementDate = "4/6/2022"))
            ))

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
    fun testMd5HashAfterSerializeAndDeserializeJacksonAndGson() {
        val statement = newBasicBankStatement()
            .update(
                feesCharged = 50.asCurrency(),
                interestCharged = 100.asCurrency(),
                transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(1000), pageMetadata = newTransactionPage(2))),
                pageMetadata = newTransactionPage(2)
            )

        val equalStatement = newBasicBankStatement()
            .update(
                feesCharged = 50.asCurrency(),
                interestCharged = 100.asCurrency(),
                transactions = listOf(newHistoryRecord(checkNumber = 1000, checkData = newCheckData(1000), pageMetadata = newTransactionPage(2))),
                pageMetadata = newTransactionPage(2)
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

        assertThat(newBankStatement(statementDate = null).update(pageMetadata = BASIC_TH_PAGE_METADATA).azureFileName())
            .isEqualTo("1234567890:WF Bank:null:test.pdf[1-1].json")

        assertThat(BankStatement(FILENAME, BankTypes.WF_BANK, BankStatementKey(FIXED_STATEMENT_DATE, null, BankTypes.WF_BANK)).azureFileName())
            .isEqualTo("null:WF Bank:4_7_2020:test.pdf.json")

        assertThat(BankStatement(FILENAME, BankTypes.WF_BANK, BankStatementKey(null, null, BankTypes.WF_BANK)).azureFileName())
            .isEqualTo("null:WF Bank:null:test.pdf.json")

        assertThat(BankStatement(FILENAME, BankTypes.WF_BANK, BankStatementKey(null, null, BankTypes.WF_BANK))
            .update(pageMetadata = newTransactionPage(3))
            .update(pageMetadata = newTransactionPage(2))
            .update(pageMetadata = newTransactionPage(7))
            .azureFileName()
        ).isEqualTo("null:WF Bank:null:test.pdf[2-7].json")
    }
}