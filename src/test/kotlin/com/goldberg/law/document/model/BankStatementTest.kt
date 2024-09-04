package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_BANK_STATEMENT
import com.goldberg.law.document.model.ModelValues.BASIC_TH_PAGE_METADATA
import com.goldberg.law.document.model.ModelValues.BASIC_TH_RECORD
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.ModelValues.newTransactionPage
import com.goldberg.law.document.model.input.StatementDataModel.Keys.ENDING_BALANCE
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.StatementSummaryEntry
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.pdf.model.DocumentType
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import io.github.oshai.kotlinlogging.KotlinLogging
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class BankStatementTest {
    private val logger = KotlinLogging.logger {}

    @Test
    fun testNetSpendingMultipleUpdates() {
        val statement = newBankStatement()
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)

        statement.update(transactions = listOf(newHistoryRecord(amount = 0.0, pageMetadata = newTransactionPage(1))))
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)
        statement.update(transactions = listOf(newHistoryRecord(amount = 500.0, pageMetadata = newTransactionPage(2))))
        assertThat(statement.getNetTransactions()).isEqualTo(500.0)
        statement.update(transactions = listOf(newHistoryRecord(amount = -500.0, pageMetadata = newTransactionPage(3))))
        assertThat(statement.getNetTransactions()).isEqualTo(0.0)
        statement.update(transactions = listOf(newHistoryRecord(amount = -500.0, pageMetadata = newTransactionPage(4))))
        assertThat(statement.getNetTransactions()).isEqualTo(-500.0)
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
        assertThat(statement.getNetTransactions()).isEqualTo(25.0)
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
        assertThat(statement.getNetTransactions()).isEqualTo(-950.0)
    }

    @Test
    fun testToCsv() {
        assertThat(BASIC_BANK_STATEMENT.toCsv())
            .isEqualTo("4/3/2020,\"test\",-500.00,,\"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER\",\"AG-12345\",4/7/2020,2,\"$FILENAME\",1,")
    }

    @Test
    fun testToCsvMultiplePages() {
        val otherPage = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 3)
        val statement = BASIC_BANK_STATEMENT.copy()
            .update(transactions = listOf(
                BASIC_TH_RECORD.copy(pageMetadata = otherPage),
                BASIC_TH_RECORD.copy(amount = 1500.00, pageMetadata = otherPage)
            ))
        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
                4/3/2020,"test",1500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
            """.trimIndent()
        )
    }

    @Test
    fun testToCsvMultiplePagesSorted() {
        val pageBefore = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 1)
        val pageBeforeTransactions = listOf(
            BASIC_TH_RECORD.copy(pageMetadata = pageBefore),
            BASIC_TH_RECORD.copy(amount = 0.0, pageMetadata = pageBefore),
            BASIC_TH_RECORD.copy(date = fromWrittenDate("3/8", "2019"), pageMetadata = pageBefore),
            BASIC_TH_RECORD.copy(date = fromWrittenDate("8/3", "2024"), pageMetadata = pageBefore)
        )

        val pageAfter = BASIC_TH_PAGE_METADATA.copy(statementPageNum = 3)
        val pageAfterTransactions = listOf(
            BASIC_TH_RECORD.copy(pageMetadata = pageAfter),
            BASIC_TH_RECORD.copy(amount = null, pageMetadata = pageAfter),
            BASIC_TH_RECORD.copy(date = fromWrittenDate("3/8", "2019"), pageMetadata = pageAfter),
            BASIC_TH_RECORD.copy(date = fromWrittenDate("8/3", "2024"), pageMetadata = pageAfter)
        )

        val statement = newBankStatement().update(
            totalPages = 5,
            beginningBalance = 500.00,
            endingBalance = 1000.00,
        ).update(transactions = listOf(BASIC_TH_RECORD))
            .update(transactions = pageAfterTransactions)
            .update(transactions = pageBeforeTransactions)
        // it doesn't sort by statement page number, so it does it based on which page was added to the statement first
        assertThat(statement.toCsv()).isEqualTo(
            """
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
                3/8/2019,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,
                4/3/2020,"test",0.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",1,
                8/3/2024,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,1,"$FILENAME",1,
            """.trimIndent()
        )
    }

    @Test
    fun testSuspiciousStatementSingleRecordIncorrect() {
        val page3 = newTransactionPage(3)
        val statement = newBankStatement().update(
            accountNumber = ACCOUNT_NUMBER,
            totalPages = 5,
            beginningBalance = 2000.00,
            endingBalance = 1000.00,
            transactions = listOf(BASIC_TH_RECORD)
        ).update(transactions = listOf(
            BASIC_TH_RECORD.copy(pageMetadata = page3),
            BASIC_TH_RECORD.copy(amount = null, pageMetadata = page3))
        )

        assertThat(statement.toCsv()).isEqualTo(
            """
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,2,"$FILENAME",1,
                4/3/2020,"test",-500.00,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",3,
                4/3/2020,"test",,,"${BankTypes.WF_BANK} - $ACCOUNT_NUMBER","AG-12345",4/7/2020,3,"$FILENAME",3,
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
            beginningBalance = 0.0,
            endingBalance = 50.0,
        ).update(transactions = listOf(
            newHistoryRecord(amount = 10.0, pageMetadata = page1),
            newHistoryRecord(amount = 20.0, pageMetadata = page1),
            newHistoryRecord(amount = 40.0, pageMetadata = page1),
        ))

        assertThat(statement.getNetTransactions()).isEqualTo(70.0)
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
            beginningBalance = 0.0,
            endingBalance = 50.0,
            accountNumber = ACCOUNT_NUMBER
        )
            .update(beginningBalance = 0.0, endingBalance = 100.0)
            .update(transactions = listOf(
                newHistoryRecord(amount = 10.0, pageMetadata = page1),
                newHistoryRecord(amount = 20.0, pageMetadata = page1),
                newHistoryRecord(amount = 40.0, pageMetadata = page1),
                newHistoryRecord(amount = 30.0, pageMetadata = page1),
                newHistoryRecord(amount = -50.0, pageMetadata = page1),
                newHistoryRecord(amount = -25.0, pageMetadata = page1),
            ))
            .update(transactions = listOf(newHistoryRecord(amount = 0.0, pageMetadata = page2)))
        assertThat(statement.getNetTransactions()).isEqualTo(25.0)
        assertThat(statement.numbersDoNotAddUp()).isTrue()
        assertThat(statement.hasSuspiciousRecords()).isTrue()

        val suspiciousReasons = statement.getSuspiciousReasons()
        assertThat(statement.isSuspicious()).isTrue()
        assertThat(suspiciousReasons).hasSize(4)
            .contains(BankStatement.SuspiciousReasons.CONTAINS_SUSPICIOUS_RECORDS)
            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(0.0, 25.0, 100.0, 100.0))
            .contains(BankStatement.SuspiciousReasons.MULTIPLE_FIELD_VALUES.format(ENDING_BALANCE, 50.0.toString(), 100.0.toString()))
            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("4/3/2020"))
    }

    @Test
    fun testNumbersAddUpCreditCard() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = 0.0, endingBalance = 500.0, transactions = listOf(BASIC_TH_RECORD))
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterest() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = 0.0, endingBalance = 750.0, transactions = listOf(BASIC_TH_RECORD), interestCharged = 250.0)
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = 0.0, endingBalance = 750.0, transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.0)
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesCharged() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = 0.0, endingBalance = 1000.0, transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.0, interestCharged = 250.0)
        assertThat(statement.isSuspicious()).isFalse()
    }

    @Test
    fun testNumbersAddUpCreditCardWithInterestAndFeesChargedAlreadyIncluded() {
        val statement = newBankStatement(classification = DocumentType.CreditCardTypes.C1_CC)
            .update(beginningBalance = 0.0, endingBalance = 500.0, transactions = listOf(BASIC_TH_RECORD), feesCharged = 250.0, interestCharged = 250.0)
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
                    statementDate = fromWrittenDate("Jan 15, 2020"),
                    accountNumber = "0100002492",
                    bankIdentifier = null,
                    startPage = 1,
                    totalPages = 2,
                    beginningBalance = 5091.61,
                    endingBalance = 2777.13,
                    interestCharged = null,
                    feesCharged = null,
            transactions = mutableListOf(
                TransactionHistoryRecord(
                    date = fromWrittenDate("Jan 1, 2020"),
                    description ="' POS Purchase MERCHANT PURCHASE TERMINAL 55432869 SQ *SQ *PIXIELANE ERIN gosq.com MD 12-24-19 12:00 AM XXXXXXXXXXXX6557",
                    amount = -2314.48,
                    pageMetadata = TransactionHistoryPageMetadata(
                        filename = "EagleBankx2492Stmt(2020)MH-000151-000190[1-3]",
                        filePageNumber = 1,
                        batesStamp = "MH100015NT",
                        statementDate = fromWrittenDate("Jan 15, 2020"),
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
                500.0,
                0.0,
                -500.0,
                1,
                setOf(BATES_STAMP),
                FILENAME,
                false,
                listOf()
        ))
    }

    @Test
    fun testToStatementSummaryMultipleRecords() {
        val otherBatesStamp = "AG-123457"
        val statement = newBankStatement().update(
            transactions = mutableListOf(
                BASIC_TH_RECORD,
                newHistoryRecord(amount = 1000.0, pageMetadata = newTransactionPage(2, batesStamp = otherBatesStamp))
            ),
            totalPages = 5,
            beginningBalance = 500.00,
            endingBalance = 1000.00
        )
        assertThat(statement.toStatementSummary()).isEqualTo(
            StatementSummaryEntry(
                ACCOUNT_NUMBER,
                BankTypes.WF_BANK,
                500.0,
                1000.0,
                500.0,
                2,
                setOf(BATES_STAMP, otherBatesStamp),
                FILENAME,
                false,
                listOf()
            ))
    }
}