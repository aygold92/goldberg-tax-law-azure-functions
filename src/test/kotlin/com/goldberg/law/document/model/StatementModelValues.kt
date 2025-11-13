package com.goldberg.law.document.model

import com.goldberg.law.document.model.ModelValues.ACCOUNT_NUMBER
import com.goldberg.law.document.model.ModelValues.BASIC_PDF_METADATA
import com.goldberg.law.document.model.ModelValues.FIXED_STATEMENT_DATE
import com.goldberg.law.document.model.ModelValues.newPdfMetadata
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.input.SummaryOfAccountsTable
import com.goldberg.law.document.model.input.SummaryOfAccountsTableRecord
import com.goldberg.law.document.model.input.tables.*
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate

class StatementModelValues {
    object FileNames {
        const val WF_BANK = "wfbank"
        const val DOUBLE_CASH = "doublecash"
        const val WF_JOINT = "wfjoint"
        const val EAGLE_BANK = "eaglebank"
        const val C1_VENTURE = "c1venture"
        const val CITI_EOY = "citiendofyear"
        const val NFCU_BANK = "nfcubank"
    }
    companion object {
        const val OTHER_FILENAME = "otherfile"
        val METADATA_1 = newPdfMetadata(pages = setOf(1))
        val METADATA_2 = newPdfMetadata(pages = setOf(2))
        val METADATA_3 = newPdfMetadata(filename = OTHER_FILENAME, pages = setOf(3))
        val METADATA_4 = newPdfMetadata(filename = OTHER_FILENAME, pages = setOf(4))
        val METADATA_5 = newPdfMetadata(pages = setOf(5))

        val STATEMENT_MODEL_1 = newStatementModel(METADATA_1)
        val STATEMENT_MODEL_2 = newStatementModel(METADATA_2)
        val STATEMENT_MODEL_3 = newStatementModel(METADATA_3)
        val STATEMENT_MODEL_4 = newStatementModel(METADATA_4)
        val STATEMENT_MODEL_5 = newStatementModel(METADATA_5)
        fun newStatementModel(pageMetadata: ClassifiedPdfMetadata = BASIC_PDF_METADATA) = StatementDataModel(
            documentType = "Test",
            pageMetadata = pageMetadata,
            date = FIXED_STATEMENT_DATE,
            accountNumber = ACCOUNT_NUMBER,
            beginningBalance = 0.asCurrency(),
            endingBalance = 0.asCurrency(),
            feesCharged = null,
            interestCharged = null,
            batesStamps = null,
            manualRecordTable = null,
        )
        val STATEMENT_MODEL_NFCU_SAME = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("June 14, 2024"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,
            accountNumber = "1663",
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(listOf(
                TransactionTableAmountRecord(date = "05-15", description = "Beginning Balance", amount = null, page = 1),
                TransactionTableAmountRecord(date = "05-15", description = "Check 152", amount = (-2500).asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "05-28", description = "POS Debit- Debit Card 6752 05-25-24 Cali Pizza Kitc IN Gaithersburg MD", amount = -56.56.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "05-31", description = "Dividend", amount = 4.69.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "06-04", description = "ATM Fee - Withdrawal 06-03-24 Bank Of America Clarksville MD", (-1).asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "06-04", description = "ATM Withdrawal 06-03-24 Bank Of America Clarksville MD", (-504).asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "06-14", description = "ATM Rebate", amount = 5.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "05-15", description = "Beginning Balance", amount = null, page = 1),
                TransactionTableAmountRecord(date = "05-31", description = "Dividend", amount = 0.12.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "06-04", description = "Withdrawal by Cash 06-04-24 Fox9 Laurel Town Ctr, MD", amount = 300.asCurrency(), page = 1),
            )),
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.NFCU_BANK, 1, BankTypes.WF_BANK)
        )
        val STATEMENT_MODEL_WF_BANK_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("February 7, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000403"),
            accountNumber = "3443",
            beginningBalance = 10124.23.asCurrency(),
            endingBalance = 6390.78.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_BANK, 1, BankTypes.WF_BANK)
        )

        val STATEMENT_MODEL_WF_BANK_1 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("February 7, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12058, date = "1/13", withdrawalAmount = 928.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "S C Herman & Ass Ppe220109 Herm01 Robert Herman", depositAmount = 183.38.asCurrency(), checkNumber = null, date = "1/14", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "WT Fed#06710 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman Srf# D1020140041101 Trn#220114020140 Rfb#", depositAmount = 20000.asCurrency(), checkNumber = null, date = "1/14", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Wire Trans Svc Charge - Sequence: 220114020140 Srf# D1020140041101 Trn#220114020140 Rfb#", depositAmount = null, checkNumber = null, date = "1/14", withdrawalAmount = 15.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12055, date = "1/18", withdrawalAmount = 100.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12054, date = "1/18", withdrawalAmount = 100.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Comp of Maryland Dir Db Rad 012022 004822018026253 x", depositAmount = null, checkNumber = null, date = "1/20", withdrawalAmount = 1173.34.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12050, date = "1/21", withdrawalAmount = 400.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12066, date = "1/21", withdrawalAmount = 930.1.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "ATM Withdrawal authorized on 01/24 5701 Connecticut Ave NW Washington DC 0004269 ATM ID 0085P Card 9899", depositAmount = null, checkNumber = null, date = "1/24", withdrawalAmount = 200.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12061, date = "1/24", withdrawalAmount = 303.64.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12063, date = "1/25", withdrawalAmount = 10884.93.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12064, date = "1/25", withdrawalAmount = 1253.67.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12062, date = "1/25", withdrawalAmount = 5607.95.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "S C Herman & Ass Ppe220123 Herm01 Robert Herman", depositAmount = 183.4.asCurrency(), checkNumber = null, date = "1/28", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12060, date = "1/28", withdrawalAmount = 343.53.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12067, date = "1/28", withdrawalAmount = 930.1.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12070, date = "2/7", withdrawalAmount = 930.1.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Interest Payment", depositAmount = 0.13.asCurrency(), checkNumber = null, date = "2/7", withdrawalAmount = null, page = 1),
            )),
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000404"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_BANK, 2, BankTypes.WF_BANK)
        )

        val STATEMENT_MODEL_WF_BANK_2 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("April 7, 2022"),
            summaryOfAccountsTable = SummaryOfAccountsTable(listOf(
                SummaryOfAccountsTableRecord(endingBalance = 11789.1.asCurrency(), accountNumber = "3443", beginningBalance = 20698.asCurrency(),),
                SummaryOfAccountsTableRecord(endingBalance = 201684.52.asCurrency(), accountNumber = "4372", beginningBalance = 201684.13.asCurrency(),)
            )),
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12087, date = "3/9", withdrawalAmount = 930.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12086, date = "3/9", withdrawalAmount = 1457.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12078, date = "3/10", withdrawalAmount = 400.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "S C Herman & Ass Ppe220306 Herm01 Robert Herman", depositAmount = 183.38.asCurrency(), checkNumber = null, date = "3/11", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "WT Fed#05582 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman CO Srf# S06207715Ea601 Trn#220318022868 Rfb#", depositAmount = 20000.asCurrency(), checkNumber = null, date = "3/18", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Wire Trans Svc Charge - Sequence: 220318022868 Srf# S06207715Ea601 Trn#220318022868 Rfb#", depositAmount = null, checkNumber = null, date = "3/18", withdrawalAmount = 15.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12088, date = "3/21", withdrawalAmount = 930.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12092, date = "3/22", withdrawalAmount = 16275.43.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12091, date = "3/22", withdrawalAmount = 5414.72.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12089, date = "3/24", withdrawalAmount = 336.62.asCurrency(), page = 1),
            )),
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000414"),
            accountNumber = "3443",
            beginningBalance = 20698.asCurrency(),
            endingBalance = 11789.1.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_BANK, 3, BankTypes.WF_BANK)
        )

        val STATEMENT_MODEL_WF_BANK_3 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("April 7, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12090, date = "3/24", withdrawalAmount = 166.86.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "S C Herman & Ass Ppe220322 Herm01 Robert Herman", depositAmount = 183.4.asCurrency(), checkNumber = null, date = "3/25", withdrawalAmount = null, page = 1),
                TransactionTableDepositWithdrawalRecord(description = "ATM Withdrawal authorized on 03/25 3700 Calvert St NW Washington DC 0002802 ATM ID 0217F Card 9899", depositAmount = null, checkNumber = null, date = "3/25", withdrawalAmount = 100.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12095, date = "3/28", withdrawalAmount = 435.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12094, date = "3/28", withdrawalAmount = 435.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12097, date = "3/30", withdrawalAmount = 930.1.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "ATM Withdrawal authorized on 04/01 3700 Calvert St NW Washington DC 0003127 ATM ID 0217F Card 9899", depositAmount = null, checkNumber = null, date = "4/1", withdrawalAmount = 100.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "ATM Withdrawal authorized on 04/04 2800 University Blvd W St Wheaton MD 0005362 ATM ID 02840 Card 9899", depositAmount = null, checkNumber = null, date = "4/4", withdrawalAmount = 20.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12099, date = "4/4", withdrawalAmount = 930.1.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Check", depositAmount = null, checkNumber = 12096, date = "4/4", withdrawalAmount = 400.asCurrency(), page = 1),
                TransactionTableDepositWithdrawalRecord(description = "Interest Payment", depositAmount = 0.15.asCurrency(), checkNumber = null, date = "4/7", withdrawalAmount = null, page = 1),
            )),
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000415"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_BANK, 5, BankTypes.WF_BANK)
        )

        val STATEMENT_MODEL_WF_BANK_4 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("April 7, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(listOf(
                TransactionTableDepositWithdrawalRecord(description = "0.39", depositAmount = null, checkNumber = null, date = "4/7", withdrawalAmount = 201684.52.asCurrency(), page = 1),
            )),
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000417"),
            accountNumber = "4372",
            beginningBalance = 201684.13.asCurrency(),
            endingBalance = 201684.52.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_BANK, 6, BankTypes.WF_BANK)
        )

        val DOUBLE_CASH_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("11/02/21"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "054241814368813582991193"),
            accountNumber = "1358",
            beginningBalance = 1881.40.asCurrency(),
            endingBalance = 1649.41.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = 0.asCurrency(),
            feesCharged = 0.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.DOUBLE_CASH, 1, "WF Bank")
        )

        val DOUBLE_CASH_1 = StatementDataModel(
            documentType = "Test",
            date = null,
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-001195"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(listOf(
                TransactionTableAmountRecord(date = "10/12", description = "ONLINE PAYMENT, THANK YOU", amount = -1881.40.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/14", description = "ONLINE PAYMENT, THANK YOU", amount = -3928.60.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/28", description = "ONLINE PAYMENT, THANK YOU", amount = 2993.32.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = ".THE HOME DEPOT #2509 BETHESDA MD", amount = 44.49.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = -55.63.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/06", description = "THE HOME DEPOT 2509 BETHESDA MD", amount = -210.94.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/11", description = "THE HOME DEPOT #2509 BETHESDA MD.", amount = 161.64.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/12.", description = "FLOOR AND DECOR 166 GAITHERSBURG MD", amount = -47.61.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = ".10/13", description = "WALMART.COM AA 800-966-6546 AR", amount = -155.66.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = ".10/13", description = "THE HOME DEPOT #2550 GAITHERSBURG MD", amount = 14.29.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/14", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = -15.90.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/19", description = "TW PERRY- SILVER SPRIN GAITHERSBURG MD", amount = -116.79.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/21", description = "THE HOME DEPOT 2550 GAITHERSBURG MD", amount = -89.90.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/27", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = -156.29.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "FLOOR AND DECOR 166 GAITHERSBURG MD", amount = 42.32.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "DC *GOV'T PAYMENT 202-442-4423 DC", amount = 451.55.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "WALMART.COM AA 800-966-6546 AR", amount = 155.66.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "THE HOME DEPOT #2550 GAITHERSBURG MD.", amount = 55.63.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/05", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = 63.48.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/06", description = "Subway 22679 Washington DC", amount = 9.67.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/06", description = "FLOOR AND DECOR 166 GAITHERSBURG MD", amount = 26.45.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/07", description = "FLOOR AND DECOR 166 GAITHERSBURG MD", amount = 31.63.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/08", description = "STROSNIDERS HARDWARE KENSINGTON MD", amount = 4.27.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/09", description = "SHELL OIL 10009244004 KENSINGTON MD", amount = 87.41.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/11", description = "Subway 22679 Washington DC:", amount = 9.67.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/11", description = "SP * IRONSUPPORTS.COM SHEBOYGAN WI", amount = 109.44.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/11", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = 3928.60.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/12", description = "TW. PERRY - SILVER SPRIN GAITHERSBURG MD", amount = 116.79.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/13", description = "THE HOME DEPOT #2558 ASPEN HILL MD", amount = 180.76.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/14", description = "THE HOME DEPOT #2509 BETHESDA MD", amount = 196.84.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/15", description = "IN *AAH CONSULTANTS LL 301-4291750 ·MD", amount = 900.00.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/19", description = "THE HOME DEPOT 2509 BETHESDA MD", amount = 212.41.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/20", description = "THE HOME DEPOT #2550 GAITHERSBURG MD", amount = 136.67.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/21", description = "THE HOME DEPOT 2550 GAITHERSBURG MD", amount = 91.36.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/21", description = "THE HOME DEPOT 2558 ASPEN HILL MD", amount = 223.43.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/22", description = "FERGUSON ENT #587 3015896662 MD", amount = 35.26.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/22", description = "THE HOME DEPOT 2509 BETHESDA MD", amount = 598.35.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/25", description = "THE HOME DEPOT #2550 GAITHERSBURG MD", amount = 52.56.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/25", description = "THE HOME DEPOT 2509 BETHESDA MD", amount = 95.44.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/26", description = "FLOOR AND DECOR 177 ALEXANDRIA VA", amount = 19.12.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/27", description = "TW PERRY- SILVER SPRIN GAITHERSBURG MD", amount = 266.36.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/27", description = "THE HOME DEPOT #2550 GAITHERSBURG MD", amount = 58.27.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/28", description = "MCG DEP TRANSFER STATI DERWOOD MD", amount = 21.28.asCurrency(), page = 1),
                TransactionTableAmountRecord(date = "10/29", description = "THE HOME DEPOT #2550 GAITHERSBURG MD", amount = 1459.79.asCurrency(), page = 1),
            )),
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = 0.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(filename = "test.pdf", page = 2, classification = "WF Bank"))

        val WF_JOINT_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("August 5, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "HERMAN-R-000629"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_JOINT, 1, BankTypes.WF_BANK)
        )

        val WF_JOINT_1 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("August 5, 2022"),
            summaryOfAccountsTable = SummaryOfAccountsTable(
                listOf(
                    SummaryOfAccountsTableRecord(accountNumber = "3443", beginningBalance = 0.07.asCurrency(), endingBalance = 48262.17.asCurrency()),
                    SummaryOfAccountsTableRecord(accountNumber = "4372", beginningBalance = 201689.60.asCurrency(), endingBalance = 150001.18.asCurrency()),
                )
            ),
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(
                listOf(
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = null, description = "Transfer IN Branch/Store - From Robert Bernard Herman DDA xxxxxx4372 8302 Woodmont Ave Bethesda MD", depositAmount = 50000.asCurrency(), withdrawalAmount = null, page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = null, description = "ATM Withdrawal authorized on 07/11 5701 Connecticut Ave NW Washington DC 0008622 ATM ID 0085P Card 9899", depositAmount = null, withdrawalAmount = 300.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = 12137, description = "Check", depositAmount = null, withdrawalAmount = 480.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/13", checkNumber = null, description = "ATM Withdrawal authorized on 07/13 5701 Connecticut Ave NW Washington DC 0008893 ATM ID 0085P Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/13", checkNumber = 12138, description = "Check", depositAmount = null, withdrawalAmount = 250.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/15", checkNumber = null, description = "S C Herman & Ass Ppe220710 Herm01 Robert Herman", depositAmount = 183.39.asCurrency(), withdrawalAmount = null, page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/15", checkNumber = null, description = "ATM Withdrawal authorized on 07/15 5701 Connecticut Ave NW Washington DC 0009152 ATM ID 0085P Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/15", checkNumber = null, description = "ATM Withdrawal authorized on 07/15 8302 Woodmont Avenue Bethesda MD 0001405 ATM ID 4737L Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                )
            ),
            batesStampsTable = null,  // newBatesStampTable(1 to "HERMAN-R-000630"),
            accountNumber = "3443",
            beginningBalance = 0.07.asCurrency(),
            endingBalance = 48262.17.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_JOINT, 2, BankTypes.WF_BANK)
        )

        val WF_JOINT_2 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("August 5, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(
                listOf(
                    TransactionTableDepositWithdrawalRecord(date = "7/15", checkNumber = 12141, description = "Check", depositAmount = null, withdrawalAmount = 4886.46.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/15", checkNumber = 12142, description = "Check", depositAmount = null, withdrawalAmount = 7751.72.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/18", checkNumber = null, description = "WT Fed#05230 Morgan Stanley and /Org=Robert B. Herman Sylvan C Herman CO Srf# S062199192E001 Tr#220718020004 Rfb#", depositAmount = 20000.asCurrency(), withdrawalAmount = null, page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/18", checkNumber = null, description = "Wire Trans Svc Charge - Sequence: 220718020004 Srf# S062199192E001 Tm#220718020004 Rfb#", depositAmount = null, withdrawalAmount = 15.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/18", checkNumber = null, description = "ATM Withdrawal authorized on 07/16 5701 Connecticut Ave NW Washington DC 0009407 ATM ID 0085P Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/18", checkNumber = 12143, description = "Check", depositAmount = null, withdrawalAmount = 379.28.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/20", checkNumber = null, description = "ATM Withdrawal authorized on 07/20 1804 Adams Mill Rd NW Washington DC 0000212 ATM ID 0221N Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/22", checkNumber = null, description = "ATM Withdrawal authorized on 07/22 3700 Calvert St NW Washington DC 0007772 ATM ID 0217F Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/25", checkNumber = 12139, description = "Check", depositAmount = null, withdrawalAmount = 250.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/26", checkNumber = null, description = "ATM Withdrawal authorized on 07/26 1934 14th St NW Washington DC 0009720 ATM ID 1234H Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/27", checkNumber = null, description = "ATM Withdrawal authorized on 07/27 1804 Adams Mill Rd NW Washington DC 0001803 ATM ID 0221N Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/29", checkNumber = null, description = "S C Herman & Ass Ppe220724 Herm01 Robert Herman", depositAmount = 684.76.asCurrency(), withdrawalAmount = null, page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/29", checkNumber = null, description = "ATM Withdrawal authorized on 07/29 1804 Adams Mill Rd NW Washington DC 0002083 ATM ID 0221N Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/1", checkNumber = null, description = "ATM Withdrawal authorized on 08/01 1804 Adams Mill Rd NW Washington DC 0002853 ATM ID 0221N Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/1", checkNumber = null, description = "ATM Withdrawal authorized on 08/01 8302 Woodmont Avenue Bethesda MD 0001871 ATM ID 4737L Card 9899", depositAmount = null, withdrawalAmount = 20.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/1", checkNumber = null, description = "Comp of Maryland Dir Db Rad 080122 004822209007133 x", depositAmount = null, withdrawalAmount = 1089.53.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/1", checkNumber = 12145, description = "Check", depositAmount = null, withdrawalAmount = 2458.99.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/2", checkNumber = 12144, description = "Check", depositAmount = null, withdrawalAmount = 250.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/3", checkNumber = null, description = "ATM Withdrawal authorized on 08/03 3700 Calvert St NW Washington DC 0008168 ATM ID 0217F Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/3", checkNumber = null, description = "ATM Withdrawal authorized on 08/03 8302 Woodmont Avenue Bethesda MD 0001918 ATM ID 4737L Card 9899", depositAmount = null, withdrawalAmount = 200.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/3", checkNumber = null, description = "Southernmgmt-Ere Sigonfile 080322 Pp2D8B Robertherman", depositAmount = null, withdrawalAmount = 2175.44.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/4", checkNumber = null, description = "ATM Withdrawal authorized on 08/04 8302 Woodmont Avenue Bethesda MD 0001933 ATM ID 4737L Card 9899", depositAmount = null, withdrawalAmount = 400.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/5", checkNumber = null, description = "ATM Withdrawal authorized on 08/05 1804 Adams Mill Rd NW Washington DC 0003887 ATM ID 0221N Card 9899", depositAmount = null, withdrawalAmount = 100.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/5", checkNumber = null, description = "Interest Payment", depositAmount = 0.37.asCurrency(), withdrawalAmount = null, page = 1),
                )
            ),
            batesStampsTable = null,
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_JOINT, 3, BankTypes.WF_BANK)
        )

        val WF_JOINT_3 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("August 5, 2022"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = TransactionTableDepositWithdrawal(
                listOf(
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = null, description = "Transfer IN Branch/Store - to Robert Bernard Herman DDA xxxxxx2578 8302 Woodmont Ave Bethesda MD", depositAmount = null, withdrawalAmount = 1088.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = null, description = "Transfer IN Branch/Store - to Robert Bernard Herman DDA xxxxxx3443 8302 Woodmont Ave Bethesda MD", depositAmount = null, withdrawalAmount = 50000.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "7/11", checkNumber = null, description = "Cash eWithdrawal in Branch/Store 07/11/2022 08:21 Am 8302 Woodmont Ave Bethesda MD 9899", depositAmount = null, withdrawalAmount = 601.60.asCurrency(), page = 1),
                    TransactionTableDepositWithdrawalRecord(date = "8/5", checkNumber = null, description = "Interest Payment", depositAmount = 1.18.asCurrency(), withdrawalAmount = null, page = 1),
                )
            ),
            batesStampsTable = null,  // newBatesStampTable(1 to "HERMAN-R-000634"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.WF_JOINT, 5, BankTypes.WF_BANK)
        )


        val EAGLE_BANK_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("May 15, 2019"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000125"),
            accountNumber = "2492",
            beginningBalance = 3675.47.asCurrency(),
            endingBalance = 4006.41.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = TransactionTableDebits(
                listOf(
                    TransactionTableDebitsRecord(date = "04-24", description = "' Electronified Check CITICARD PAYMENT CHECK PYMT 190424 3171", subtractions = 1845.77.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-01", description = "ATM Withdrawal CASH WITHDRAWAL TERMINAL SA1326 M&T 11325 SEVEN LO POTOMAC MD 05-01-19 9:17 AM XXXXXXXXXXXX6557", subtractions = 200.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-01", description = "' ATM Surcharge SURCHARGE AMOUNT TERMINAL SA1326 M&T 11325 SEVEN LO POTOMAC MD 05-01-19 9:17 AM XXXXXXXXXXXX6557", subtractions = 3.50.asCurrency(), page = 1),
                )
            ),
            transactionTableCredits = null,
            transactionTableChecks = TransactionTableChecks(
                listOf(
                    TransactionTableChecksRecord(date = "04-22", number = 3167, amount = 200.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "05-06", number = 3174, amount = 1554.39.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "04-23", number = 3172, amount = 875.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "04-29", number = 3173, amount = 427.50.asCurrency(), page = 1),
                )
            ),
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.EAGLE_BANK, 6, BankTypes.WF_BANK)
        )
        val EAGLE_BANK_1 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("May 15, 2019"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000126"),
            accountNumber = "2492",
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = TransactionTableDebits(
                listOf(
                    TransactionTableDebitsRecord(date = "05-07", description = "' ATM Withdrawal CASH WITHDRAWAL TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 05-07-19 12:22 PM XXXXXXXXXXXX6557", subtractions = 200.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-07", description = "' ATM Surcharge SURCHARGE AMOUNT TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 05-07-19 12:22 PM XXXXXXXXXXXX6557", subtractions = 3.95.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-13", description = "' ATM Withdrawal CASH WITHDRAWAL TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 05-13-19 1:45 PM XXXXXXXXXXXX6557", subtractions = 300.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-13", description = "' ATM Surcharge SURCHARGE AMOUNT TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 05-13-19 1:45 PM XXXXXXXXXXXX6557", subtractions = 3.95.asCurrency(), page = 1),
                )
            ),
            transactionTableCredits = TransactionTableCredits(
                listOf(
                    TransactionTableCreditsRecord(date = "04-22", description = "Deposit", additions = 1150.asCurrency(), page = 1),
                    TransactionTableCreditsRecord(date = "04-23", description = "Deposit", additions = 875.asCurrency(), page = 1),
                    TransactionTableCreditsRecord(date = "04-29", description = "Deposit", additions = 1200.asCurrency(), page = 1),
                    TransactionTableCreditsRecord(date = "05-03", description = "Deposit", additions = 2500.asCurrency(), page = 1),
                    TransactionTableCreditsRecord(date = "05-13", description = "Deposit", additions = 220.asCurrency(), page = 1),
                )
            ),
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.EAGLE_BANK, 7, BankTypes.WF_BANK)
        )
        val EAGLE_BANK_2 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("June 15, 2019"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000128"),
            accountNumber = "2492",
            beginningBalance = 4006.41.asCurrency(),
            endingBalance = 3139.31.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = TransactionTableDebits(
                listOf(
                    TransactionTableDebitsRecord(date = "05-24", description = "' ATM Withdrawal CASH WITHDRAWAL TERMINAL T9752019 12505 PARK POTOMAC AVE POTOMAC MD 05-24-19 11:50 AM XXXXXXXXXXXX6557", subtractions = 200.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-28", description = "ATM Withdrawal CASH WITHDRAWAL TERMINAL P406674 12200 ROCKVILLE PI", subtractions = 300.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "05-28", description = "KUS ROCKVILLE MD 05-25-19 4:18 PM XXXXXXXXXXXX6557 ATM Surcharge SURCHARGE AMOUNT TERMINAL P406674 12200 ROCKVILLE PI KUS ROCKVILLE MD 05-25-19 4.18 PM XXXXXXXXXXXX6557", subtractions = 2.50.asCurrency(), page = 1),
                )
            ),
            transactionTableCredits = null,
            transactionTableChecks = TransactionTableChecks(
                listOf(
                    TransactionTableChecksRecord(date = "05-21", number = 3175, amount = 427.50.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "06-03", number = 3203, amount = 1209.15.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "05-30", number = 3176, amount = 1000.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "06-10", number = 3204, amount = 220.asCurrency(), page = 1),
                    TransactionTableChecksRecord(date = "05-29", number = 3202, amount = 204.asCurrency(), page = 1),
                )
            ),
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.EAGLE_BANK, 8, BankTypes.WF_BANK)
        )
        val EAGLE_BANK_3 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("June 15, 2019"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,
            accountNumber = "2492",
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = TransactionTableDebits(
                listOf(
                    TransactionTableDebitsRecord(date = null, description = "' ATM Withdrawal CASH WITHDRAWAL TERMINAL T9752019 12505 PARK POTOMAC AVE POTOMAC MD 06-05-19 1:56 PM XXXXXXXXXXXX6557", subtractions = 300.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "06-07", description = "'ATM Withdrawal CASH WITHDRAWAL TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 06-07-19 2:29 PM XXXXXXXXXXXX6557", subtractions = 200.asCurrency(), page = 1),
                    TransactionTableDebitsRecord(date = "06-07", description = "' ATM Surcharge SURCHARGE AMOUNT TERMINAL P375574 2511 FIRE ROAD STE A12 EHT NJ 06-07-19 2:29 PM XXXXXXXXXXXX6557", subtractions = 3.95.asCurrency(), page = 1),
                )
            ),
            transactionTableCredits = TransactionTableCredits(
                listOf(
                    TransactionTableCreditsRecord(date = "05-23", description = "Deposit", additions = 1000.asCurrency(), page = 1),
                    TransactionTableCreditsRecord(date = "06-06", description = "Deposit", additions = 2200.asCurrency(), page = 1),
                )
            ),
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = null,
            pageMetadata = ClassifiedPdfMetadata(FileNames.EAGLE_BANK, 9, BankTypes.WF_BANK)
        )


        val C1_VENTURE_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("Jun 09 2021"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "0059000059060007$201"),
            accountNumber = "5695",
            beginningBalance = 0.asCurrency(),
            endingBalance = 59.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = 0.asCurrency(),
            feesCharged = 59.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.C1_VENTURE, 1, BankTypes.WF_BANK)
        )

        val C1_VENTURE_1 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("Jun 09 2021"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-000714"),
            accountNumber = "5695",
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(
                listOf(
                    TransactionTableAmountRecord(date = "Jun 9", description = "CAPITAL ONE MEMBER FEE", amount = 59.asCurrency(), page = 1),
                )
            ),
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = 0.asCurrency(),
            feesCharged = 59.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.C1_VENTURE, 3, BankTypes.WF_BANK)
        )


        val CITI_END_OF_YEAR_0 = StatementDataModel(
            documentType = "Test",
            date = normalizeDate("Jan 07 2021"),
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-002249"),
            accountNumber = "2374",
            beginningBalance = 4500.36.asCurrency(),
            endingBalance = 9128.51.asCurrency(),
            transactionTableAmount = null,
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = 84.90.asCurrency(),
            feesCharged = 29.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.CITI_EOY, 1, BankTypes.WF_BANK)
        )
        val CITI_END_OF_YEAR_1 = StatementDataModel(
            documentType = "Test",
            date = null,
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "3AAG000120 MH-002251"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(
                listOf(
                    TransactionTableAmountRecord(date = "12/14", description = "EB 2020 BULL RUN FEST 8014137200 CA", amount = -27.41.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/29", description = "DOORDASH*MORTONS THE S SAN FRANCISCO CA", amount = -208.53.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "01/06", description = "AMZN Mktp US Amzn.com/bill WA", amount = -21.85.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/08", description = "VTG*Growing Minds LLC ROCKVILLE . MD", amount = 196.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/08", description = "EXXONMOBIL 97436182 KENSINGTON MD", amount = 48.80.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/10", description = "THE PEDIATRIC CARE CEN 301-564-5880 MD", amount = 393.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/11", description = "APPLE.COM/BILL 866-712-7753 CA", amount = 9.99.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/11", description = "TARGET.COM *", amount = null, page = 1),
                    TransactionTableAmountRecord(date = null, description = "800-591-3869 MN", amount = 84.27.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "2/12", description = "APPLE.COM/BILL 866-712-7753 CA", amount = 9.99.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/14", description = "AMZN Mktp US*2B8SG1SL2 Amzn.com/bill WA", amount = 8.86.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/14", description = "AMZN MKTP US*OK39N8RE3 AMZN.COM/BILL WA", amount = 17.97.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = ".12/14", description = "EB 2020 BULL RUN FEST 8014137200 CA", amount = 32.80.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/14:", description = "VTG*Growing Minds LLC ROCKVILLE MD", amount = 196.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/15", description = "AMZN Mktp US*IE2BZOWM3 Amzn.com/bill WA", amount = 11.45.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/15", description = "LIBERTY FALLS VETERINA 3017622070. MD", amount = 179.71.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/16", description = "AMZN MKTP US*QS8TO3AZ3 AMZN.COM/BILL WA", amount = 19.07.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/16", description = "AMZN Mktp US*A62Y31F53 Amzn.com/bill WA", amount = 41.68.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/17:", description = "AMZN Mktp.US*SI3ZOOHX3 Amzn.com/bill. WA", amount = 21.85.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = ".12/17:", description = "AMAZON.COM*QX6GP6703 A AMZN.COM/BILL WA", amount = 49.55.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/17", description = "AMAZON.COM*B475P5S73 A AMZN.COM/BILL WA", amount = 296.50.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/18", description = "CARD MY YARD: 5129311269 TX", amount = 270.30.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = ":12/18", description = "HONEYBAKED HAM #0099 678-966-3100 GA", amount = 160.85.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = ":12/19", description = "AMZN MKTPUS*UV8DQ3V53 AMZN.COM/BILL WA", amount = 10.58.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/19", description = "Gregorio's Trattoria P Potomac MD", amount = 96.72.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/21", description = "VTG*Growing Minds LLC ROCKVILLE MD", amount = 196.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/22", description = "GFM*GoFindMe* Baby Magg Redwood City CA", amount = 110.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/23", description = "DUNKIN #339788 Q35 KENSINGTON MD", amount = 7.41.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/23", description = "EB 2020 BULL RUN FEST: 8014137200 CA", amount = 32.80.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/23", description = "EXXONMOBIL 97436182 KENSINGTON MD", amount = 13.73.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/24", description = "TST* O DONNELL S MARKE ROCKVILLE MD", amount = 246.36.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/24", description = "AT&T*BILL PAYMENT 8003310500 TX", amount = 253.62.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/26.", description = "EB 2020 BULL RUN FEST 8014137200 CA", amount = 32.80.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/29", description = "· DOORDASH*MORTONS THE S SAN FRANCISCO CA", amount = 208.53.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/29", description = "SUNFLOWER BAKERY 240-361-3698 MD", amount = 27.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/29", description = "Center for Assessment CHEVY CHASE MD", amount = 162.50.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/29", description = "Center for Assessment CHEVY CHASE MD", amount = 975.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/31", description = "STARBUCKS STORE 19853 KENSINGTON MD", amount = 84.49.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "12/31", description = "TST* O DONNELL S MARKE ROCKVILLE MD.", amount = 165.86.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "01/06", description = "JSSA ONLINE 3018813700 MD", amount = 100.asCurrency(), page = 1),
                    TransactionTableAmountRecord(date = "01/07", description = "LATE FEE - DEC PAYMENT PAST DUE", amount = 29.asCurrency(), page = 1),
                )
            ),
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = null,
            feesCharged = 29.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.CITI_EOY, 2, BankTypes.WF_BANK)
        )

        val CITI_END_OF_YEAR_2 = StatementDataModel(
            documentType = "Test",
            date = null,
            summaryOfAccountsTable = null,
            transactionTableDepositWithdrawal = null,
            batesStampsTable = null,  // newBatesStampTable(1 to "MH-002252"),
            accountNumber = null,
            beginningBalance = null,
            endingBalance = null,
            transactionTableAmount = TransactionTableAmount(
                listOf(
                    TransactionTableAmountRecord(date = "01/07", description = "INTEREST CHARGED TO STANDARD PURCH", amount = 84.90.asCurrency(), page = 1)
                )
            ),
            transactionTableCreditsCharges = null,
            transactionTableDebits = null,
            transactionTableCredits = null,
            transactionTableChecks = null,
            interestCharged = 84.90.asCurrency(),
            feesCharged = 29.asCurrency(),
            pageMetadata = ClassifiedPdfMetadata(FileNames.CITI_EOY, 3, BankTypes.WF_BANK)
        )
    }
}