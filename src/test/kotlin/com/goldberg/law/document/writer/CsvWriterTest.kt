package com.goldberg.law.document.writer

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_BATES_STAMP
import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.CHECK_FILE_PAGE
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.newBasicBankStatement
import com.goldberg.law.document.model.ModelValues.newCheckData
import com.goldberg.law.document.model.ModelValues.newHistoryRecord
import com.goldberg.law.document.model.output.*
import com.goldberg.law.pdf.model.DocumentType.BankTypes
import com.goldberg.law.util.fromWrittenDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class CsvWriterTest {
    @Test
    fun testRecordContent() {
        val writer = TestCsvWriter()
        writer.writeRecordsToCsv(FILENAME, listOf(
            newBasicBankStatement(),
            newBasicBankStatement().copy(transactions = mutableListOf(newHistoryRecord(checkNumber = 1234, description = "Check", checkData = newCheckData(1234))))
        ))

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "Transaction Date","Description","Amount","","Account","Bates Stamp","Statement Date","Statement Page #","Filename","File Page #","Check Bates Stamp","Check Filename","Check File Page #"
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,
                
                4/3/2020,"Check 1234",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
            """.trimIndent()
        )
    }

    @Test
    fun testRecordContentWithSuspiciousReasons() {
        val writer = TestCsvWriter()
        writer.writeRecordsToCsv(FILENAME, listOf(
            newBasicBankStatement(),
            newBasicBankStatement().update(transactions = mutableListOf(newHistoryRecord(checkNumber = 1234, amount = -50.0, checkData = newCheckData(1234))))
        ))

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "Transaction Date","Description","Amount","","Account","Bates Stamp","Statement Date","Statement Page #","Filename","File Page #","Check Bates Stamp","Check Filename","Check File Page #"
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,
                
                "[Beginning balance (500.000000) + net deposits (-550.000000) != ending balance (0.000000). Expected (-500.000000), Contains suspicious records, [4/3/2020] Record has a check number but the description does not say "check"]"
                4/3/2020,"test",-500.00,,"WF Bank - 1234567890","AG-12345",4/7/2020,2,"test.pdf",1,
                4/3/2020,"test Check 1234",-50.00,,"WF Bank - 1234567890","$BATES_STAMP",4/7/2020,2,"$FILENAME",1,"$CHECK_BATES_STAMP","$CHECK_FILENAME",$CHECK_FILE_PAGE
            """.trimIndent()
        )
    }

    @Test
    fun testAccountSummaryContent() {
        val otherAccount = "123789"
        val writer = TestCsvWriter()
        val entry1 = AccountSummaryEntry(
            ACCOUNT_NUMBER, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            emptyList(), emptyList()
        )
        val entry2 = AccountSummaryEntry(
            otherAccount, BankTypes.WF_BANK, fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!,
            listOf("7/2020", "8/2020"), listOf(fromWrittenDate("6 5, 2020")!!, fromWrittenDate("9 2, 2020")!!)
        )

        writer.writeAccountSummaryToCsv(FILENAME, listOf(entry1, entry2))

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "Account","Bank","First Statement","Last Statement","Missing Statements","Suspicious Statements"
                "1234567890",WF Bank,6/5/2020,9/2/2020,"[]","[]"
                "123789",WF Bank,6/5/2020,9/2/2020,"[7/2020, 8/2020]","[6/5/2020, 9/2/2020]"
            """.trimIndent()
        )
    }

    @Test
    fun testStatementSummaryContent() {
        val writer = TestCsvWriter()

        val entry1 = StatementSummaryEntry(
            ACCOUNT_NUMBER,
            BankTypes.WF_BANK,
            500.0,
            1000.0,
            500.0,
            2,
            setOf(BATES_STAMP, "AG-123457"),
            FILENAME,
            false,
            listOf()
        )

        val entry2 = StatementSummaryEntry(
            ACCOUNT_NUMBER,
            BankTypes.WF_BANK,
            500.0,
            1000.0,
            500.0,
            2,
            setOf(BATES_STAMP, "AG-123457"),
            FILENAME,
            true,
            listOf(TransactionHistoryRecord.SuspiciousReasons.NO_DATE, BankStatement.SuspiciousReasons.INCORRECT_DATES)
        )

        writer.writeStatementSummaryToCsv(FILENAME, listOf(entry1, entry2))

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "Account","Bank","Beginning Balance","Ending Balance","Net Transactions","Number of Transactions","Bates Stamps","Filename","Status","Suspicious Reasons"
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME",Verified,
                "$ACCOUNT_NUMBER","${BankTypes.WF_BANK}",500.00,1000.00,500.00,2,"[$BATES_STAMP, AG-123457]","$FILENAME",ERROR,"[${TransactionHistoryRecord.SuspiciousReasons.NO_DATE}, ${BankStatement.SuspiciousReasons.INCORRECT_DATES}]"
            """.trimIndent()
        )
    }

    @Test
    fun testCheckSummaryContentNormal() {
        val writer = TestCsvWriter()

        val key1 = CheckDataKey(ACCOUNT_NUMBER, 1000)
        val key2 = CheckDataKey(ACCOUNT_NUMBER, 1001)
        val allChecksMap = mapOf(
            key1 to newCheckData(1000),
            key2 to newCheckData(1001)
        )
        val checksNotFound = setOf<CheckDataKey>()
        val checksNotUsed = setOf<CheckDataKey>()
        writer.writeCheckSummaryToCsv(FILENAME, allChecksMap, checksNotFound, checksNotUsed)

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "${CsvWriter.CHECK_IMAGE_NOT_FOUND_HEADER}","${CsvWriter.CHECK_IMAGE_NOT_USED_HEADER}"
                "[]","[]"
                
                "Account","Check Number","Description","Date","Amount","Bates Stamp","Filename","File Page #"
                "1234567890",1000,"test",4/3/2020,-500.00,"CH-12345","checkfile",7
                "1234567890",1001,"test",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent()
        )
    }

    @Test
    fun testCheckSummaryContentNoChecksFound() {
        val writer = TestCsvWriter()

        val key1 = CheckDataKey(ACCOUNT_NUMBER, 1000)
        val key2 = CheckDataKey(ACCOUNT_NUMBER, 1001)
        val allChecksMap = mapOf(
            key1 to newCheckData(1000),
            key2 to newCheckData(1001)
        )
        val checksNotFound = setOf(key1)
        val checksNotUsed = setOf(key2)
        writer.writeCheckSummaryToCsv(FILENAME, allChecksMap, checksNotFound, checksNotUsed)

        assertThat(writer.fileName).isEqualTo(FILENAME)
        assertThat(writer.content).isEqualTo(
            """
                "${CsvWriter.CHECK_IMAGE_NOT_FOUND_HEADER}","${CsvWriter.CHECK_IMAGE_NOT_USED_HEADER}"
                "[1234567890 - 1000]","[1234567890 - 1001]"
                
                "Account","Check Number","Description","Date","Amount","Bates Stamp","Filename","File Page #"
                "1234567890",1000,"test",4/3/2020,-500.00,"CH-12345","checkfile",7
                "1234567890",1001,"test",4/3/2020,-500.00,"CH-12345","checkfile",7
            """.trimIndent()
        )
    }
}