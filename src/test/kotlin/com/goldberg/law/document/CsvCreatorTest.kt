package com.goldberg.law.document

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.CHECK_FILE_PAGE
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE_DATE
import com.goldberg.law.document.model.ModelValues.newBankStatement
import com.goldberg.law.document.model.ModelValues.newBasicBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.*
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvCreatorTest {
    private val csvCreator = CsvCreator()

    @Test
    fun testRecordContent() {
        val content = csvCreator.recordsToCsv(listOf(
            newBasicBankStatement(),
            newBasicBankStatement().copy(transactions = mutableListOf(newHistoryRecord(checkNumber = 1234, description = "Check", checkData = newCheckData(1234))))
        ))

        assertThat(content).isEqualTo(
            """
                "Transaction Date","Description","Amount","","Account","Bates Stamp","Statement Date","Statement Page #","Filename","File Page #","Check Bates Stamp","Check Filename","Check File Page #"
                Verified,,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,,
                ,,=SUM(C2:C3),,"WF Bank - 1234567890",,4/7/2020
                
                Verified,,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"Check 1234: Some Guy - check desc",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
                ,,=SUM(C6:C7),,"WF Bank - 1234567890",,4/7/2020
            """.trimIndent()
        )
    }

    @Test
    fun testRecordContentWithSuspiciousReasons() {
        val content = csvCreator.recordsToCsv(listOf(
            newBasicBankStatement(),
            newBasicBankStatement().update(transactions = mutableListOf(newHistoryRecord(checkNumber = 1234, amount = -50.0, checkData = newCheckData(1234))))
        ))

        assertThat(content).isEqualTo(
            """
                "Transaction Date","Description","Amount","","Account","Bates Stamp","Statement Date","Statement Page #","Filename","File Page #","Check Bates Stamp","Check Filename","Check File Page #"
                Verified,,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,,
                ,,=SUM(C2:C3),,"WF Bank - 1234567890",,4/7/2020
                
                "[Beginning balance (500.00) + net transactions (-550.00) != ending balance (0.00). Expected (-500.00), Contains suspicious records, [4/3/2020] Record has a check number but the description does not say "check"]",,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test Check 1234: Some Guy - check desc",-50.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
                ,,=SUM(C6:C8),,"WF Bank - 1234567890",,4/7/2020
            """.trimIndent()
        )
    }

    @Test
    fun testRecordContentWithSuspiciousReasonsManyRecordsAndStatements() {
        val content = csvCreator.recordsToCsv(listOf(
            newBasicBankStatement(),
            newBasicBankStatement().update(transactions = mutableListOf(newHistoryRecord(checkNumber = 1234, amount = -50.0, checkData = newCheckData(1234)))),
            newBasicBankStatement().update(transactions = mutableListOf(
                newHistoryRecord(amount = -50.0),
                newHistoryRecord(checkNumber = 1235, description = "Check", amount = -50.0, checkData = newCheckData(1234)),
                newHistoryRecord(amount = -50.0),
                newHistoryRecord(amount = 50.0),
                newHistoryRecord(amount = 50.0),
            )),
            newBasicBankStatement(),
            newBankStatement(),
            newBasicBankStatement().update(transactions = mutableListOf(newHistoryRecord(amount = -50.0), newHistoryRecord(amount = -25.0))),
        ))

        assertThat(content).isEqualTo(
            """
                "Transaction Date","Description","Amount","","Account","Bates Stamp","Statement Date","Statement Page #","Filename","File Page #","Check Bates Stamp","Check Filename","Check File Page #"
                Verified,,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,,
                ,,=SUM(C2:C3),,"WF Bank - 1234567890",,4/7/2020
                
                "[Beginning balance (500.00) + net transactions (-550.00) != ending balance (0.00). Expected (-500.00), Contains suspicious records, [4/3/2020] Record has a check number but the description does not say "check"]",,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test Check 1234: Some Guy - check desc",-50.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
                ,,=SUM(C6:C8),,"WF Bank - 1234567890",,4/7/2020
                
                "[Beginning balance (500.00) + net transactions (-550.00) != ending balance (0.00). Expected (-500.00)]",,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test",-50.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"Check 1235: Some Guy - check desc",-50.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
                4/3/2020,"test",-50.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test",50.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test",50.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                ,,=SUM(C11:C17),,"WF Bank - 1234567890",,4/7/2020
                
                Verified,,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,,
                ,,=SUM(C20:C21),,"WF Bank - 1234567890",,4/7/2020
                
                "[Missing fields: [BeginningBalance, EndingBalance], No transactions recorded]",,,,"WF Bank - 1234567890",,4/7/2020
                
                ,,=SUM(C24:C25),,"WF Bank - 1234567890",,4/7/2020
                
                "[Beginning balance (500.00) + net transactions (-575.00) != ending balance (0.00). Expected (-500.00)]",,,,"WF Bank - 1234567890",,4/7/2020
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                4/3/2020,"test",-50.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,,
                4/3/2020,"test",-25.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,,
                ,,=SUM(C28:C31),,"WF Bank - 1234567890",,4/7/2020
            """.trimIndent()
        )
    }

    @Test
    fun testAccountSummaryContent() {
        val otherAccount = "123789"
        val entry1 = AccountSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            emptyList(), emptyList()
        )
        val entry2 = AccountSummaryEntry(
            otherAccount, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            listOf("7/2020", "8/2020"), listOf(fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!)
        )

        val content = csvCreator.accountSummaryToCsv(listOf(entry1, entry2))

        assertThat(content).isEqualTo(
            """
                "Account","Bank","First Statement","Last Statement","Missing Statements","Suspicious Statements"
                "1234567890",WF Bank,6/5/2020,9/2/2020,"[]","[]"
                "123789",WF Bank,6/5/2020,9/2/2020,"[7/2020, 8/2020]","[6/5/2020, 9/2/2020]"
            """.trimIndent()
        )
    }

    @Test
    fun testStatementSummaryContent() {
        val entry1 = StatementSummaryEntry(
            ACCOUNT_NUMBER,
            BankTypes.WF_BANK,
            FIXED_STATEMENT_DATE_DATE,
            500.asCurrency(),
            1000.asCurrency(),
            500.asCurrency(),
            2,
            setOf(BATES_STAMP, "AG-123457"),
            FILENAME,
            setOf(3,4,5),
            false,
            listOf()
        )

        val entry2 = StatementSummaryEntry(
            ACCOUNT_NUMBER,
            BankTypes.WF_BANK,
            FIXED_STATEMENT_DATE_DATE,
            500.asCurrency(),
            1000.asCurrency(),
            500.asCurrency(),
            2,
            setOf(BATES_STAMP, "AG-123457"),
            FILENAME,
            setOf(1,4,5),
            true,
            listOf(TransactionHistoryRecord.SuspiciousReasons.NO_DATE, BankStatement.SuspiciousReasons.INCORRECT_DATES)
        )

        val content = csvCreator.statementSummaryToCsv(listOf(entry1, entry2))

        assertThat(content).isEqualTo(
            """
                "Account","Bank","Statement Date","Beginning Balance","Ending Balance","Net Transactions","Number of Transactions","Bates Stamps","Filename","File Page #s","Status","Suspicious Reasons"
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",4/7/2020,500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME","[3, 4, 5]",Verified,
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",4/7/2020,500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME","[1, 4, 5]",ERROR,"[${TransactionHistoryRecord.SuspiciousReasons.NO_DATE}, ${BankStatement.SuspiciousReasons.INCORRECT_DATES}]"
            """.trimIndent()
        )
    }

    @Test
    fun testCheckSummaryContentNormal() {
        val allChecks = listOf(newCheckData(1000), newCheckData(1001))

        val checksNotFound = setOf<CheckDataKey>()
        val checksNotUsed = setOf<CheckDataKey>()
        val content = csvCreator.checkSummaryToCsv(allChecks, checksNotFound, checksNotUsed)

        assertThat(content).isEqualTo(
            """
                "${CsvCreator.CHECK_IMAGE_NOT_FOUND_HEADER}","${CsvCreator.CHECK_IMAGE_NOT_USED_HEADER}"
                "[]","[]"
                
                "Account","Check Number","Description","Date","Amount","Bates Stamp","Filename","File Page #"
                "1234567890",1000,"check desc",4/3/2020,-500.00,"CH-12345","checkfile",7
                "1234567890",1001,"check desc",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent()
        )
    }

    @Test
    fun testCheckSummaryContentNoChecksFound() {
        val allChecks = listOf(newCheckData(1000), newCheckData(1001))
        val key1 = CheckDataKey(ACCOUNT_NUMBER, 1000)
        val key2 = CheckDataKey(ACCOUNT_NUMBER, 1001)

        val checksNotFound = setOf(key1)
        val checksNotUsed = setOf(key2)
        val content = csvCreator.checkSummaryToCsv(allChecks, checksNotFound, checksNotUsed)

        assertThat(content).isEqualTo(
            """
                "${CsvCreator.CHECK_IMAGE_NOT_FOUND_HEADER}","${CsvCreator.CHECK_IMAGE_NOT_USED_HEADER}"
                "[1234567890 - 1000]","[1234567890 - 1001]"
                
                "Account","Check Number","Description","Date","Amount","Bates Stamp","Filename","File Page #"
                "1234567890",1000,"check desc",4/3/2020,-500.00,"CH-12345","checkfile",7
                "1234567890",1001,"check desc",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent()
        )
    }
}