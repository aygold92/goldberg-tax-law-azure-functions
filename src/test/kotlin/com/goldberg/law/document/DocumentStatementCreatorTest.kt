package com.goldberg.law.document

import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.output.BankStatement
import com.goldberg.law.document.model.output.TransactionHistoryPageMetadata
import com.goldberg.law.document.model.output.TransactionHistoryRecord
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.normalizeDate
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.ValueSource
import java.nio.file.Files
import java.nio.file.Paths

class DocumentStatementCreatorTest {
//    private val statementCreator = DocumentStatementCreator()
//
//    @Test
//    fun testUseLastStatementAccount() {
//        val page1 = StatementDataModel(
//            date = "4/7/2022",
//            pageNum = 1,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = "1234",
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 1, BankTypes.WF_BANK),
//        )
//
//        val page2 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 2, BankTypes.WF_BANK),
//        )
//
//        val page3 = StatementDataModel(
//            date = "5/7/2022",
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 3, BankTypes.WF_BANK),
//        )
//
//        val page4 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = "1235",
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 4, BankTypes.WF_BANK),
//        )
//
//        val page5 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 5, BankTypes.EAGLE_BANK),
//        )
//
//        val result = statementCreator.createBankStatements(listOf(page1, page2, page3, page4, page5)).toList()
//
//        assertThat(result).hasSize(4)
//
//        assertThat(result[0].date).isEqualTo("4/7/2022")
//        assertThat(result[0].accountNumber).isEqualTo("1234")
//
//        assertThat(result[1].date).isEqualTo("5/7/2022")
//        assertThat(result[1].accountNumber).isNull()
//
//        assertThat(result[2].date).isEqualTo("5/7/2022")
//        assertThat(result[2].accountNumber).isEqualTo("1235")
//
//        assertThat(result[3].date).isNull()
//        assertThat(result[3].accountNumber).isNull()
//    }
//
//    @Test
//    fun testStatementCreatorProcessesInCorrectOrder() {
//        val page1 = StatementDataModel(
//            date = "4/7/2022",
//            pageNum = 1,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = "1234",
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 1, BankTypes.WF_BANK),
//        )
//
//        val page2 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 2, BankTypes.WF_BANK),
//        )
//
//        val page3 = StatementDataModel(
//            date = "5/7/2022",
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 3, BankTypes.WF_BANK),
//        )
//
//        val page4 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = "1235",
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 4, BankTypes.WF_BANK),
//        )
//
//        val page5 = StatementDataModel(
//            date = null,
//            pageNum = 2,
//            totalPages = 5,
//            batesStamp = "123",
//            accountNumber = null,
//            bankIdentifier = null, summaryOfAccountsTable = null, transactionTableDepositWithdrawal = null, beginningBalance = null, endingBalance = null, transactionTableAmount = null, transactionTableCreditsCharges = null, transactionTableDebits = null, transactionTableCredits = null, transactionTableChecks = null, interestCharged = null, feesCharged = null,
//            pageMetadata = PdfDocumentPageMetadata(FILENAME, 5, BankTypes.EAGLE_BANK),
//        )
//
//        val result = statementCreator.createBankStatements(listOf(page1, page2, page3, page4, page5)).toList()
//        val result2 = statementCreator.createBankStatements(listOf(page5, page2, page4, page3, page1)).toList()
//
//        assertThat(result).isEqualTo(result2)
//    }
//
//    @Test
//    fun testFullIndividualStatementBank() {
//        val models = listOf(
//            StatementModelValues.STATEMENT_MODEL_WF_BANK_0,
//            StatementModelValues.STATEMENT_MODEL_WF_BANK_1,
//            StatementModelValues.STATEMENT_MODEL_WF_BANK_2,
//            StatementModelValues.STATEMENT_MODEL_WF_BANK_3,
//            StatementModelValues.STATEMENT_MODEL_WF_BANK_4,
//        )
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        val firstStatementDate = normalizeDate("Feb 7, 2022")
//
//        assertThat(result.size).isEqualTo(3)
//        val metadata1 = TransactionHistoryPageMetadata(
//            filePageNumber = 1,
//            batesStamp = "MH-000403",
//            statementPageNum = 1
//        )
//
//        val metadata2 = TransactionHistoryPageMetadata(
//            filePageNumber = 2,
//            batesStamp = "MH-000404",
//            statementPageNum = 2
//        )
//
//        val records = models.flatMap { it.transactionTableDepositWithdrawal?.records ?: listOf() }
//        assertThat(result[0]).isEqualTo(
//            BankStatement(
//                filename = StatementModelValues.FileNames.WF_BANK,
//                classification = BankTypes.WF_BANK,
//                date = firstStatementDate,
//                accountNumber = "3443",
//                startPage = null,
//                totalPages = 4,
//                beginningBalance = 10124.23.asCurrency(),
//                endingBalance = 6390.78.asCurrency(),
//                transactions = mutableListOf(
//                    TransactionHistoryRecord(id = records[0].id, checkNumber = 12058, date = "1/13/2022", description = "Check", amount = (-928.0).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[1].id, checkNumber = null, date = "1/14/2022", description = "S C Herman & Ass Ppe220109 Herm01 Robert Herman", amount = 183.38.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[2].id, checkNumber = null, date = "1/14/2022", description = "WT Fed#06710 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman Srf# D1020140041101 Trn#220114020140 Rfb#", amount = 20000.0.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[3].id, checkNumber = null, date = "1/14/2022", description = "Wire Trans Svc Charge - Sequence: 220114020140 Srf# D1020140041101 Trn#220114020140 Rfb#", amount = -15.0.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[4].id, checkNumber = 12055, date = "1/18/2022", description = "Check", amount = (-100.0).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[5].id, checkNumber = 12054, date = "1/18/2022", description = "Check", amount = (-100.0).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[6].id, checkNumber = null, date = "1/20/2022", description = "Comp of Maryland Dir Db Rad 012022 004822018026253 x", amount = -1173.34.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[7].id, checkNumber = 12050, date = "1/21/2022", description = "Check", amount = (-400.0).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[8].id, checkNumber = 12066, date = "1/21/2022", description = "Check", amount = (-930.1).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[9].id, checkNumber = null, date = "1/24/2022", description = "ATM Withdrawal authorized on 01/24 5701 Connecticut Ave NW Washington DC 0004269 ATM ID 0085P Card 9899", amount = -200.0.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[10].id, checkNumber = 12061, date = "1/24/2022", description = "Check", amount = (-303.64).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[11].id, checkNumber = 12063, date = "1/25/2022", description = "Check", amount = (-10884.93).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[12].id, checkNumber = 12064, date = "1/25/2022", description = "Check", amount = (-1253.67).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[13].id, checkNumber = 12062, date = "1/25/2022", description = "Check", amount = (-5607.95).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[14].id, checkNumber = null, date = "1/28/2022", description = "S C Herman & Ass Ppe220123 Herm01 Robert Herman", amount = 183.4.asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[15].id, checkNumber = 12060, date = "1/28/2022", description = "Check", amount = (-343.53).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[16].id, checkNumber = 12067, date = "1/28/2022", description = "Check", amount = (-930.1).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[17].id, checkNumber = 12070, date = "2/7/2022", description = "Check", amount = (-930.1).asCurrency(), pageMetadata = metadata2),
//                    TransactionHistoryRecord(id = records[18].id, checkNumber = null, date = "2/7/2022", description = "Interest Payment", amount = 0.13.asCurrency(), pageMetadata = metadata2),
//                ),
//                pages = mutableSetOf(metadata1, metadata2)
//            )
//        )
//
//        val secondStatementDate = normalizeDate("Apr 7, 2022")
//        val metadata3 = TransactionHistoryPageMetadata(filePageNumber = 3, batesStamp = "MH-000414", statementPageNum = 2)
//        val metadata4 =  TransactionHistoryPageMetadata(filePageNumber = 5, batesStamp = "MH-000415", statementPageNum = 3)
//        assertThat(result[1]).isEqualTo(
//            BankStatement(
//                filename = StatementModelValues.FileNames.WF_BANK,
//                classification = BankTypes.WF_BANK,
//                date = secondStatementDate,
//                accountNumber = "3443",
//                startPage = 2,
//                totalPages = 6,
//                beginningBalance = 20698.0.asCurrency(),
//                endingBalance = 11789.10.asCurrency(),
//                transactions = mutableListOf(
//                    // 1st page
//                    TransactionHistoryRecord(id = records[19].id, description = "Check", checkNumber = 12087, date = normalizeDate("3/9 2022"), amount = (-930.0).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[20].id, description = "Check", checkNumber = 12086, date = normalizeDate("3/9 2022"), amount = (-1457.0).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[21].id, description = "Check", checkNumber = 12078, date = normalizeDate("3/10 2022"), amount = (-400.0).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[22].id, description = "S C Herman & Ass Ppe220306 Herm01 Robert Herman", checkNumber = null, date = normalizeDate("3/11 2022"), amount = 183.38.asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[23].id, description = "WT Fed#05582 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman CO Srf# S06207715Ea601 Trn#220318022868 Rfb#", checkNumber = null, date = normalizeDate("3/18 2022"), amount = 20000.0.asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[24].id, description = "Wire Trans Svc Charge - Sequence: 220318022868 Srf# S06207715Ea601 Trn#220318022868 Rfb#", checkNumber = null, date = normalizeDate("3/18 2022"), amount = (-15.0).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[25].id, description = "Check", checkNumber = 12088, date = normalizeDate("3/21 2022"), amount = (-930.0).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[26].id, description = "Check", checkNumber = 12092, date = normalizeDate("3/22 2022"), amount = (-16275.43).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[27].id, description = "Check", checkNumber = 12091, date = normalizeDate("3/22 2022"), amount = (-5414.72).asCurrency(), pageMetadata = metadata3),
//                    TransactionHistoryRecord(id = records[28].id, description = "Check", checkNumber = 12089, date = normalizeDate("3/24 2022"), amount = (-336.62).asCurrency(), pageMetadata = metadata3),
//                    // 2nd page
//                    TransactionHistoryRecord(id = records[29].id, description = "Check",checkNumber = 12090, date = normalizeDate("3/24 2022"), amount = (-166.86).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[30].id, description = "S C Herman & Ass Ppe220322 Herm01 Robert Herman",checkNumber = null, date = normalizeDate("3/25 2022"), amount = 183.4.asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[31].id, description = "ATM Withdrawal authorized on 03/25 3700 Calvert St NW Washington DC 0002802 ATM ID 0217F Card 9899",checkNumber = null, date = normalizeDate("3/25 2022"), amount = (-100.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[32].id, description = "Check",checkNumber = 12095, date = normalizeDate("3/28 2022"), amount = (-435.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[33].id, description = "Check",checkNumber = 12094, date = normalizeDate("3/28 2022"), amount = (-435.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[34].id, description = "Check",checkNumber = 12097, date = normalizeDate("3/30 2022"), amount = (-930.1).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[35].id, description = "ATM Withdrawal authorized on 04/01 3700 Calvert St NW Washington DC 0003127 ATM ID 0217F Card 9899",checkNumber = null, date = normalizeDate("4/1/ 022"), amount = (-100.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[36].id, description = "ATM Withdrawal authorized on 04/04 2800 University Blvd W St Wheaton MD 0005362 ATM ID 02840 Card 9899",checkNumber = null, date = normalizeDate("4/4/ 022"), amount = (-20.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[37].id, description = "Check",checkNumber = 12099, date = normalizeDate("4/4/ 2022"), amount = (-930.1).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[38].id, description = "Check",checkNumber = 12096, date = normalizeDate("4/4/ 2022"), amount = (-400.0).asCurrency(), pageMetadata = metadata4),
//                    TransactionHistoryRecord(id = records[39].id, description = "Interest Payment",checkNumber = null, date = normalizeDate("4/7/ 2022"), amount = 0.15.asCurrency(), pageMetadata = metadata4),
//                ),
//                pages = mutableSetOf(metadata3, metadata4)
//            )
//        )
//
//        val metadata5 =  TransactionHistoryPageMetadata(filePageNumber = 6, batesStamp = "MH-000417", statementPageNum = 5)
//        assertThat(result[2]).isEqualTo(
//            BankStatement(
//                filename = StatementModelValues.FileNames.WF_BANK,
//                classification = BankTypes.WF_BANK,
//                date = secondStatementDate,
//                accountNumber = "4372",
//                startPage = 5,
//                totalPages = 6,
//                beginningBalance = 201684.13.asCurrency(),
//                endingBalance = 201684.52.asCurrency(),
//                transactions = mutableListOf(
//                    TransactionHistoryRecord(id = records[40].id, date = "4/7/2022", checkNumber = null, description = "0.39", amount = (-201684.52).asCurrency(), pageMetadata = metadata5)
//                ),
//                pages = mutableSetOf(metadata5)
//            )
//        )
//    }
//
//    @Test
//    fun testFullStatementDoubleCash() {
//        val models = listOf(StatementModelValues.DOUBLE_CASH_0, StatementModelValues.DOUBLE_CASH_1)
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        val statement = result[0]
//        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("11/02/21"))
//        assertThat(statement.accountNumber).isEqualTo("1358")
//
//        assertThat(statement.getSuspiciousReasons())
//            .hasSize(1)
//            .contains(BankStatement.SuspiciousReasons.BALANCE_DOES_NOT_ADD_UP.format(1881.40.asCurrency(), 6195.49, 1649.41, -231.99))
//
//    }
//
//    @Test
//    fun testFullStatementC1SignatureJoint() {
//        val models = listOf(StatementModelValues.C1_VENTURE_0, StatementModelValues.C1_VENTURE_1)
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        val statement = result[0]
//        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("Jun 09, 2021"))
//        assertThat(statement.accountNumber).isEqualTo("5695")
//        assertThat(statement.getNetTransactions()).isEqualTo((59.0).asCurrency())
//        assertThat(statement.getSuspiciousReasons()).isEmpty()
//    }
//
//    @Test
//    fun testFullStatementEagleBank() {
//        val models = listOf(StatementModelValues.EAGLE_BANK_0, StatementModelValues.EAGLE_BANK_1, StatementModelValues.EAGLE_BANK_2, StatementModelValues.EAGLE_BANK_3)
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(2)
//
//        val statement = result[0]
//        assertThat(statement.statementDate).isEqualTo(fromWrittenDate("May 15, 2019"))
//        assertThat(statement.accountNumber).isEqualTo("2492")
//        println(statement.transactions)
//        assertThat(statement.getNetTransactions()).isEqualTo(330.94.asCurrency())
//        assertThat(statement.getSuspiciousReasons()).isEmpty()
//
//        val statement2 = result[1]
//        assertThat(statement2.statementDate).isEqualTo(fromWrittenDate("June 15, 2019"))
//        assertThat(statement2.accountNumber).isEqualTo("2492")
//        assertThat(statement2.getNetTransactions()).isEqualTo(-867.1.asCurrency())
//        assertThat(statement2.getSuspiciousReasons()).hasSize(2)  // one of the records is missing a date
//    }
//
//    @Test
//    fun testFullStatementCitiEndOfYear() {
//        val models = listOf(
//            StatementModelValues.CITI_END_OF_YEAR_0,
//            StatementModelValues.CITI_END_OF_YEAR_1,
//            StatementModelValues.CITI_END_OF_YEAR_2
//        )
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(1)
//
//        val statement = result[0]
//        assertThat(statement.isSuspicious()).isTrue()
//        assertThat(statement.getSuspiciousReasons())
//            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_DATE)
//            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("12/11/2020"))
//            .contains(BankStatement.SuspiciousReasons.INCORRECT_DATES.format(listOf("2/12/2021")))
//    }
//
//    @Test
//    fun testFullJointStatementSummaryOfAccounts() {
//        val models = listOf(
//            StatementModelValues.WF_JOINT_0,
//            StatementModelValues.WF_JOINT_1,
//            StatementModelValues.WF_JOINT_2,
//            StatementModelValues.WF_JOINT_3
//        )
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(3)
//
//        val statement1 = result[0]
//        assertThat(statement1.isSuspicious()).isTrue()
//        assertThat(statement1.accountNumber).isNull()
//        assertThat(statement1.beginningBalance).isNull()
//        assertThat(statement1.endingBalance).isNull()
//        assertThat(statement1.transactions).isEmpty()
//
//        val statement2 = result[1]
//        assertThat(statement2.isSuspicious()).isFalse()
//        assertThat(statement2.transactions.size).isEqualTo(32)
//
//        val statement3 = result[2]
//        assertThat(statement3.isSuspicious()).isFalse()
//        assertThat(statement3.transactions.size).isEqualTo(4)
//        assertThat(statement3.getNetTransactions()).isEqualTo(-51688.42.asCurrency())
//    }
//
//    @Test
//    fun testFullJointStatementSummaryOfAccountsPlusOtherAccount() {
//        val models = listOf(
//            StatementModelValues.WF_JOINT_0,
//            StatementModelValues.WF_JOINT_1,
//            StatementModelValues.WF_JOINT_2,
//            StatementModelValues.WF_JOINT_3,
//            StatementModelValues.CITI_END_OF_YEAR_0,
//            StatementModelValues.CITI_END_OF_YEAR_1,
//            StatementModelValues.CITI_END_OF_YEAR_2,
//        )
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(4)
//
//        val statement0 = result[0]
//        assertThat(statement0.isSuspicious()).isTrue()
//        assertThat(statement0.getSuspiciousReasons())
//            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_DATE)
//            .contains(TransactionHistoryRecord.SuspiciousReasons.NO_AMOUNT.format("12/11/2020"))
//            .contains(BankStatement.SuspiciousReasons.INCORRECT_DATES.format(listOf("2/12/2021")))
//
//        val statement1 = result[1]
//        assertThat(statement1.isSuspicious()).isTrue()
//        assertThat(statement1.accountNumber).isNull()
//        assertThat(statement1.beginningBalance).isNull()
//        assertThat(statement1.endingBalance).isNull()
//        assertThat(statement1.transactions).isEmpty()
//
//        val statement2 = result[2]
//        assertThat(statement2.isSuspicious()).isFalse()
//        assertThat(statement2.transactions.size).isEqualTo(32)
//
//        val statement3 = result[3]
//        assertThat(statement3.isSuspicious()).isFalse()
//        assertThat(statement3.transactions.size).isEqualTo(4)
//        assertThat(statement3.getNetTransactions()).isEqualTo(-51688.42.asCurrency())
//    }
//
//    @Test
//    fun testNFCUJointStatementsOnSamePage() {
//        val models = listOf(
//            readFileRelative("NFCU_Same[0].json"),
//            readFileRelative("NFCU_Same[1].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(2)
//
//        assertThat(result[0].accountNumber).isEqualTo("1663")
//        assertThat(result[0].transactions.size).isEqualTo(6)
//        assertThat(result[0].getNetTransactions()).isEqualTo((-3051.87).asCurrency())
//        assertThat(result[0].isSuspicious()).isFalse()
//
//        assertThat(result[1].accountNumber).isEqualTo("9667")
//        assertThat(result[1].transactions.size).isEqualTo(2)
//        assertThat(result[1].getNetTransactions()).isEqualTo((-299.88).asCurrency())
//        assertThat(result[1].isSuspicious()).isFalse()
//    }
//
//    @Test
//    fun testNFCUJointStatementsOnDifferentPages() {
//        val models = listOf(
//            readFileRelative("NFCU_Different[0].json"),
//            readFileRelative("NFCU_Different[1].json"),
//            readFileRelative("NFCU_Different[2].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(2)
//
//        assertThat(result[0].accountNumber).isEqualTo("1663")
//        assertThat(result[0].transactions.size).isEqualTo(14)
//        assertThat(result[0].getNetTransactions()).isEqualTo((6209.1).asCurrency())
//        assertThat(result[0].isSuspicious()).isFalse()
//
//        assertThat(result[1].accountNumber).isEqualTo("9667")
//        assertThat(result[1].transactions.size).isEqualTo(1)
//        assertThat(result[1].getNetTransactions()).isEqualTo(.05.asCurrency())
//        assertThat(result[1].isSuspicious()).isFalse()
//    }
//
//    @Test
//    fun testNFCUSingleAccountStatement() {
//        val models = listOf(
//            readFileRelative("NFCU_SingleAccount[0].json"),
//            readFileRelative("NFCU_SingleAccount[1].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(1)
//
//        assertThat(result[0].accountNumber).isEqualTo("9667")
//        assertThat(result[0].transactions.size).isEqualTo(4)
//        assertThat(result[0].getNetTransactions()).isEqualTo(5762.23.asCurrency())
//        assertThat(result[0].isSuspicious()).isFalse()
//    }
//
//    @Test
//    fun testNFCUAllInOne() {
//        val models = listOf(
//            readFileRelative("NFCU_Different[0].json"),
//            readFileRelative("NFCU_Different[1].json"),
//            readFileRelative("NFCU_Different[2].json"),
//            readFileRelative("NFCU_SingleAccount[0].json"),
//            readFileRelative("NFCU_SingleAccount[1].json"),
//            readFileRelative("NFCU_Same[0].json"),
//            readFileRelative("NFCU_Same[1].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(5)
//
//        val singleAccountStmt = result.find { it.date == "3/14/2022" }!!
//        val differentPageStmt1663 = result.find { it.filename == "NFCU_2024_Different_Page.pdf" && it.accountNumber == "1663" }!!
//        val differentPageStmt9667 = result.find { it.filename == "NFCU_2024_Different_Page.pdf" && it.accountNumber == "9667" }!!
//        val samePageStmt1663 = result.find { it.filename == "NFCU_2024_SamePage.pdf" && it.accountNumber == "1663" }!!
//        val samePageStmt9667 = result.find { it.filename == "NFCU_2024_SamePage.pdf" && it.accountNumber == "9667" }!!
//
//        assertThat(differentPageStmt1663.accountNumber).isEqualTo("1663")
//        assertThat(differentPageStmt1663.transactions.size).isEqualTo(14)
//        assertThat(differentPageStmt1663.getNetTransactions()).isEqualTo((6209.1).asCurrency())
//        assertThat(differentPageStmt1663.isSuspicious()).isFalse()
//
//        assertThat(differentPageStmt9667.accountNumber).isEqualTo("9667")
//        assertThat(differentPageStmt9667.transactions.size).isEqualTo(1)
//        assertThat(differentPageStmt9667.getNetTransactions()).isEqualTo(.05.asCurrency())
//        assertThat(differentPageStmt9667.isSuspicious()).isFalse()
//
//        assertThat(singleAccountStmt.accountNumber).isEqualTo("9667")
//        assertThat(singleAccountStmt.transactions.size).isEqualTo(4)
//        assertThat(singleAccountStmt.getNetTransactions()).isEqualTo(5762.23.asCurrency())
//        assertThat(singleAccountStmt.isSuspicious()).isFalse()
//
//        assertThat(samePageStmt1663.accountNumber).isEqualTo("1663")
//        assertThat(samePageStmt1663.transactions.size).isEqualTo(6)
//        assertThat(samePageStmt1663.getNetTransactions()).isEqualTo((-3051.87).asCurrency())
//        assertThat(samePageStmt1663.isSuspicious()).isFalse()
//
//        assertThat(samePageStmt9667.accountNumber).isEqualTo("9667")
//        assertThat(samePageStmt9667.transactions.size).isEqualTo(2)
//        assertThat(samePageStmt9667.getNetTransactions()).isEqualTo((-299.88).asCurrency())
//        assertThat(samePageStmt9667.isSuspicious()).isFalse()
//    }
//
//    @ParameterizedTest
//    @ValueSource(strings = ["EOYWithYear.json", "EOYNoYear.json"])
//    fun testEOYTransactions(filename: String) {
//        val models = listOf(
//            readFileRelative(filename),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(1)
//        val statement = result[0]
//
//        assertThat(statement.accountNumber).isEqualTo("8558")
//        assertThat(statement.transactions.size).isEqualTo(4)
//        assertThat(statement.transactions.map { it.transactionDate }).containsAll(listOf(
//            fromWrittenDate("12/22/21"),
//            fromWrittenDate("12/30/21"),
//            fromWrittenDate("01/03/22"),
//            fromWrittenDate("01/07/22"),
//        ))
//    }
//
//    @Test
//    fun testNFCUNotWorking() {
//        val models = listOf(
//            readFileRelative("NFCU_NotWorking[0].json"),
//            readFileRelative("NFCU_NotWorking[1].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(2)
//
//        assertThat(result[0].accountNumber).isEqualTo("1663")
//        assertThat(result[0].transactions.size).isEqualTo(9)
//        assertThat(result[0].isSuspicious()).isFalse()
//
//        assertThat(result[1].accountNumber).isEqualTo("9667")
//        assertThat(result[1].transactions.size).isEqualTo(1)
//        assertThat(result[1].isSuspicious()).isFalse()
//    }
//
//    @Test
//    fun testNFCUMany() {
//        val models = listOf(
//            readFileRelative("NFCU_Many[0].json"),
//            readFileRelative("NFCU_Many[1].json"),
//            readFileRelative("NFCU_Many[2].json"),
//            readFileRelative("NFCU_Many[3].json"),
//            readFileRelative("NFCU_Many[4].json"),
//            readFileRelative("NFCU_Many[5].json"),
//        ).map { OBJECT_MAPPER.readValue(it, StatementDataModel::class.java) }
//
//        val result = statementCreator.createBankStatements(models).toList()
//
//        assertThat(result).hasSize(4)
//
//        assertThat(result[0].accountNumber).isEqualTo("4737")
//        assertThat(result[0].pages).isEqualTo(setOf(
//            TransactionHistoryPageMetadata(2, "RC005625", 2),
//            TransactionHistoryPageMetadata(3, "RC005626", 3),
//            TransactionHistoryPageMetadata(4, "RC005627", 4),
//            TransactionHistoryPageMetadata(5, "RC005628", 5)
//        ))
//        assertThat(result[1].accountNumber).isEqualTo("3863")
//        assertThat(result[1].transactions).hasSize(4)
//        assertThat(result[1].pages).isEqualTo(setOf(
//            TransactionHistoryPageMetadata(6, "RC005629", 6),
//        ))
//        assertThat(result[2].accountNumber).isEqualTo("4307")
//        assertThat(result[2].transactions).hasSize(1)
//        assertThat(result[2].pages).isEqualTo(setOf(
//            TransactionHistoryPageMetadata(6, "RC005629", 6),
//        ))
//        assertThat(result[3].accountNumber).isEqualTo("5057")
//        assertThat(result[3].transactions).isEmpty()
//        assertThat(result[3].pages).isEmpty()
//
//    }
//
//    companion object {
//        val PATH_TO_CLASS = "/" + DocumentStatementCreator::class.java.packageName.replace('.', '/')
//
//        private val OBJECT_MAPPER = ObjectMapper()
//        fun readFileRelative(filename: String): String =
//            Files.readString(Paths.get(javaClass.getResource("$PATH_TO_CLASS/statement/$filename")!!.toURI()))
//    }

}