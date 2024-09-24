package com.goldberg.law

import com.goldberg.law.datamanager.DataManager
import com.goldberg.law.document.*
import com.nhaarman.mockitokotlin2.*
import org.junit.jupiter.api.Test
import org.mockito.Mock

class PdfExtractorMainTest {
    @Mock
    private val dataManager: DataManager = mock()
    @Mock
    private val classifier: DocumentClassifier = mock()
    @Mock
    private val dataExtractor: DocumentDataExtractor = mock()
    @Mock
    private val statementCreator: DocumentStatementCreator = mock()
    @Mock
    private val accountNormalizer: AccountNormalizer = mock()
    @Mock
    private val checkToStatementMatcher: CheckToStatementMatcher = mock()
    @Mock
    private val accountSummaryCreator: AccountSummaryCreator = mock()

    val main = PdfExtractorMain(
        dataManager,
        classifier,
        dataExtractor,
        statementCreator,
        accountNormalizer,
        checkToStatementMatcher,
        accountSummaryCreator,
        1
    )

//    @BeforeEach
//    fun setup() {
//        whenever(splitter.splitPdf(eq(PDF_DOCUMENT), any(), any())).thenReturn(listOf(SPLIT_PDF_1, SPLIT_PDF_2))
//        whenever(splitter.splitPdf(eq(PDF_DOCUMENT_2), any(), any())).thenReturn(listOf(SPLIT_PDF_21, SPLIT_PDF_22))
//        whenever(splitter.splitPdf(eq(PDF_DOCUMENT_3), any(), any())).thenReturn(listOf(SPLIT_PDF_31, SPLIT_PDF_32))
//        whenever(classifier.classifyDocument(SPLIT_PDF_1)).thenReturn(CLASSIFIED_PDF_1)
//        whenever(classifier.classifyDocument(SPLIT_PDF_2)).thenReturn(CLASSIFIED_PDF_2)
//        whenever(classifier.classifyDocument(SPLIT_PDF_21)).thenReturn(CLASSIFIED_PDF_2_1)
//        whenever(classifier.classifyDocument(SPLIT_PDF_22)).thenReturn(CLASSIFIED_PDF_2_2)
//        whenever(classifier.classifyDocument(SPLIT_PDF_31)).thenReturn(CLASSIFIED_PDF_3_1)
//        whenever(classifier.classifyDocument(SPLIT_PDF_32)).thenReturn(CLASSIFIED_PDF_3_2)
//
//        whenever(dataExtractor.extractTransactionHistory(listOf(CLASSIFIED_PDF_1, CLASSIFIED_PDF_2)))
//            .thenReturn(listOf(BANK_STATEMENT_1, BANK_STATEMENT_2))
//
//        whenever(dataExtractor.extractTransactionHistory(listOf(CLASSIFIED_PDF_2_1, CLASSIFIED_PDF_2_2)))
//            .thenReturn(listOf(BANK_STATEMENT_3))
//
//        whenever(dataExtractor.extractCheckData(listOf(CLASSIFIED_PDF_3_1, CLASSIFIED_PDF_3_2))).thenReturn(mapOf(
//                CHECK_DATA_KEY_1000 to CHECK_DATA_1000,
//                CHECK_DATA_KEY_1001 to CHECK_DATA_1001
//        ))
//
//        whenever(accountSummaryCreator.createSummary(any())).thenReturn(listOf(ACCOUNT_SUMMARY_ENTRY))
//    }
//
//    @Test
//    fun testNormal() {
//        whenever(pdfLoader.loadPdfs(any())).thenReturn(listOf(PDF_DOCUMENT, PDF_DOCUMENT_2, PDF_DOCUMENT_3))
//
//        whenever(checkToStatementMatcher.matchChecksWithStatements(any(), any()))
//            .thenReturn(Triple(listOf(BANK_STATEMENT_1, BANK_STATEMENT_2_WITH_CHECK_DATA, BANK_STATEMENT_3_WITH_CHECK_DATA), setOf(), setOf()))
//
//        val firstChecksMap = mapOf(
//            CHECK_DATA_KEY_1000 to CHECK_DATA_1000,
//            CHECK_DATA_KEY_1001 to CHECK_DATA_1001
//        )
//
//        val normalizedCheckMap = mapOf(
//            CHECK_DATA_KEY_1000 to CHECK_DATA_1000,
//            CHECK_DATA_KEY_1001 to CHECK_DATA_1001,
//            CheckDataKey(ACCOUNT_NUMBER, 1002) to newCheckData(1002)
//        )
//        whenever(accountNormalizer.normalizeAccounts(any(), any())).thenReturn(
//            Pair(listOf(BASIC_BANK_STATEMENT), normalizedCheckMap)
//        )
//
//        main.run(REQUEST)
//
//        listOf(SPLIT_PDF_1,  SPLIT_PDF_2,  SPLIT_PDF_21,  SPLIT_PDF_22,  SPLIT_PDF_31,  SPLIT_PDF_32).forEach {
//            verify(classifier).classifyDocument(it)
//        }
//
//        verify(dataExtractor).extractTransactionHistory(listOf(CLASSIFIED_PDF_1, CLASSIFIED_PDF_2))
//        verify(dataExtractor).extractTransactionHistory(listOf(CLASSIFIED_PDF_2_1, CLASSIFIED_PDF_2_2))
//        verify(dataExtractor).extractCheckData(listOf(CLASSIFIED_PDF_3_1, CLASSIFIED_PDF_3_2))
//
//        verify(accountNormalizer).normalizeAccounts(listOf(BANK_STATEMENT_1, BANK_STATEMENT_2, BANK_STATEMENT_3), firstChecksMap)
//        verify(checkToStatementMatcher).matchChecksWithStatements(listOf(BASIC_BANK_STATEMENT), normalizedCheckMap)
//        val finalStatementList = listOf(BANK_STATEMENT_1, BANK_STATEMENT_2_WITH_CHECK_DATA, BANK_STATEMENT_3_WITH_CHECK_DATA)
//        verify(accountSummaryCreator).createSummary(finalStatementList)
//
//
//        verify(csvWriter).writeRecordsToCsv(RECORDS_FILENAME, finalStatementList)
//        verify(csvWriter).writeAccountSummaryToCsv(ACCOUNT_SUMMARY_FILENAME, listOf(ACCOUNT_SUMMARY_ENTRY))
//        verify(csvWriter).writeStatementSummaryToCsv(STATEMENT_SUMMARY_FILENAME, listOf(BANK_STATEMENT_1.toStatementSummary(), BANK_STATEMENT_2_WITH_CHECK_DATA.toStatementSummary(), BANK_STATEMENT_3_WITH_CHECK_DATA.toStatementSummary()))
//
//        verify(csvWriter).writeCheckSummaryToCsv(CHECK_SUMMARY_FILENAME, normalizedCheckMap, setOf(), setOf())
//    }
//
//    @Test
//    fun testChecksNotUsed() {
//        whenever(pdfLoader.loadPdfs(any())).thenReturn(listOf(PDF_DOCUMENT, PDF_DOCUMENT_3))
//        whenever(checkToStatementMatcher.matchChecksWithStatements(any(), any()))
//            .thenReturn(Triple(listOf(BANK_STATEMENT_1, BANK_STATEMENT_2_WITH_CHECK_DATA), setOf(CHECK_DATA_KEY_1001), setOf()))
//
//        val normalizedCheckMap = mapOf(
//            CHECK_DATA_KEY_1000 to CHECK_DATA_1000,
//            CHECK_DATA_KEY_1001 to CHECK_DATA_1001,
//            CheckDataKey(ACCOUNT_NUMBER, 1002) to newCheckData(1002)
//        )
//        whenever(accountNormalizer.normalizeAccounts(any(), any())).thenReturn(
//            Pair(listOf(BASIC_BANK_STATEMENT), normalizedCheckMap)
//        )
//
//        main.run(REQUEST)
//
//        listOf(SPLIT_PDF_1,  SPLIT_PDF_2,  SPLIT_PDF_31,  SPLIT_PDF_32).forEach {
//            verify(classifier).classifyDocument(it)
//        }
//
//        verify(dataExtractor).extractTransactionHistory(listOf(CLASSIFIED_PDF_1, CLASSIFIED_PDF_2))
//        verify(dataExtractor).extractCheckData(listOf(CLASSIFIED_PDF_3_1, CLASSIFIED_PDF_3_2))
//
//        val finalStatementList = listOf(BANK_STATEMENT_1, BANK_STATEMENT_2_WITH_CHECK_DATA)
//        verify(accountSummaryCreator).createSummary(finalStatementList)
//
//
//        verify(csvWriter).writeRecordsToCsv(RECORDS_FILENAME, finalStatementList)
//        verify(csvWriter).writeAccountSummaryToCsv(ACCOUNT_SUMMARY_FILENAME, listOf(ACCOUNT_SUMMARY_ENTRY))
//        verify(csvWriter).writeStatementSummaryToCsv(STATEMENT_SUMMARY_FILENAME, listOf(BANK_STATEMENT_1.toStatementSummary(), BANK_STATEMENT_2_WITH_CHECK_DATA.toStatementSummary()))
//
//        verify(csvWriter).writeCheckSummaryToCsv(CHECK_SUMMARY_FILENAME, normalizedCheckMap, setOf(), setOf(CHECK_DATA_KEY_1001))
//    }
//
//    @Test
//    fun testChecksNotFound() {
//        whenever(pdfLoader.loadPdfs(any())).thenReturn(listOf(PDF_DOCUMENT, PDF_DOCUMENT_2))
//
//        whenever(checkToStatementMatcher.matchChecksWithStatements(any(), any()))
//            .thenReturn(Triple(listOf(BANK_STATEMENT_1, BANK_STATEMENT_2, BANK_STATEMENT_3), setOf(), setOf(CHECK_DATA_KEY_1000, CHECK_DATA_KEY_1001)))
//
//        whenever(accountNormalizer.normalizeAccounts(any(), any())).thenReturn(
//            Pair(listOf(BASIC_BANK_STATEMENT), mapOf())
//        )
//
//        main.run(REQUEST)
//
//        listOf(SPLIT_PDF_1,  SPLIT_PDF_2,  SPLIT_PDF_21,  SPLIT_PDF_22).forEach {
//            verify(classifier).classifyDocument(it)
//        }
//
//        verify(dataExtractor).extractTransactionHistory(listOf(CLASSIFIED_PDF_1, CLASSIFIED_PDF_2))
//        verify(dataExtractor).extractTransactionHistory(listOf(CLASSIFIED_PDF_2_1, CLASSIFIED_PDF_2_2))
//
//        val finalStatementList = listOf(BANK_STATEMENT_1, BANK_STATEMENT_2, BANK_STATEMENT_3)
//        verify(accountSummaryCreator).createSummary(finalStatementList)
//
//
//        verify(csvWriter).writeRecordsToCsv(RECORDS_FILENAME, finalStatementList)
//        verify(csvWriter).writeAccountSummaryToCsv(ACCOUNT_SUMMARY_FILENAME, listOf(ACCOUNT_SUMMARY_ENTRY))
//        verify(csvWriter).writeStatementSummaryToCsv(STATEMENT_SUMMARY_FILENAME, listOf(BANK_STATEMENT_1.toStatementSummary(), BANK_STATEMENT_2.toStatementSummary(), BANK_STATEMENT_3.toStatementSummary()))
//
//        verify(csvWriter).writeCheckSummaryToCsv(CHECK_SUMMARY_FILENAME, mapOf(), setOf(CHECK_DATA_KEY_1000, CHECK_DATA_KEY_1001), setOf())
//    }
//
//    companion object {
//        val OUTPUT_DIRECTORY = "output"
//        val OUTPUT_FILE = "file"
//        val FILE_PREFIX = "$OUTPUT_DIRECTORY/${OUTPUT_FILE}"
//        val CHECK_SUMMARY_FILENAME = "${FILE_PREFIX}_CheckSummary"
//        val RECORDS_FILENAME = "${FILE_PREFIX}_Records"
//        val ACCOUNT_SUMMARY_FILENAME = "${FILE_PREFIX}_AccountSummary"
//        val STATEMENT_SUMMARY_FILENAME = "${FILE_PREFIX}_StatementSummary"
//        val REQUEST = AnalyzeDocumentRequest().apply {
//            inputFile = "testDir"
//            outputDirectory = OUTPUT_DIRECTORY
//            outputFile = OUTPUT_FILE
//            allPages = true
//        }
//
//        val ACCOUNT_SUMMARY_ENTRY = AccountSummaryEntry(
//            ACCOUNT_NUMBER, DocumentType.BankTypes.WF_BANK, FIXED_STATEMENT_DATE, FIXED_STATEMENT_DATE, listOf(), listOf()
//        )
//
//        val PD_DOCUMENT_1_PAGE =  PDDocument().apply { addPage(PDPage()) }
//        val PD_DOCUMENT_2_PAGE =  PDDocument().apply {
//            addPage(PDPage())
//            addPage(PDPage())
//        }
//        val PDF_DOCUMENT = PdfDocument("test", PD_DOCUMENT_2_PAGE)
//        val PDF_DOCUMENT_2 = PdfDocument("test2", PD_DOCUMENT_2_PAGE)
//        val PDF_DOCUMENT_3 = PdfDocument("test3", PD_DOCUMENT_2_PAGE)
//        val SPLIT_PDF_1 = PdfDocument("test[1]", PD_DOCUMENT_1_PAGE)
//        val SPLIT_PDF_2 = PdfDocument("test[2]", PD_DOCUMENT_1_PAGE)
//        val SPLIT_PDF_21 = PdfDocument("test2[1]", PD_DOCUMENT_1_PAGE)
//        val SPLIT_PDF_22 = PdfDocument("test2[2]", PD_DOCUMENT_1_PAGE)
//        val SPLIT_PDF_31 = PdfDocument("test3[1]", PD_DOCUMENT_1_PAGE)
//        val SPLIT_PDF_32 = PdfDocument("test3[2]", PD_DOCUMENT_1_PAGE)
//
//        val CLASSIFIED_PDF_1 = ClassifiedPdfDocument("test[1]", PD_DOCUMENT_1_PAGE, listOf(1), DocumentType.BankTypes.WF_BANK)
//        val CLASSIFIED_PDF_2 = ClassifiedPdfDocument("test[2]", PD_DOCUMENT_1_PAGE, listOf(2), DocumentType.BankTypes.WF_BANK)
//        val CLASSIFIED_PDF_2_1 = ClassifiedPdfDocument("test2[1]", PD_DOCUMENT_1_PAGE, listOf(1), DocumentType.BankTypes.WF_BANK)
//        val CLASSIFIED_PDF_2_2 = ClassifiedPdfDocument("test2[2]", PD_DOCUMENT_1_PAGE, listOf(2), DocumentType.BankTypes.WF_BANK)
//        val CLASSIFIED_PDF_3_1 = ClassifiedPdfDocument("test3[1]", PD_DOCUMENT_1_PAGE, listOf(1), DocumentType.CheckTypes.EAGLE_BANK_CHECK)
//        val CLASSIFIED_PDF_3_2 = ClassifiedPdfDocument("test3[2]", PD_DOCUMENT_1_PAGE, listOf(2), DocumentType.CheckTypes.EAGLE_BANK_CHECK)
//
//        val BANK_STATEMENT_1 = newBankStatement(filename = "test").update(
//            transactions = mutableListOf(BASIC_TH_RECORD),
//            totalPages = 5,
//            beginningBalance = 500.asCurrency(),
//            endingBalance = ZERO
//        )
//
//        val BANK_STATEMENT_2 = newBankStatement(filename = "test").update(
//            transactions = mutableListOf(
//                BASIC_TH_RECORD,
//                newHistoryRecord(checkNumber = 1000, description = "Check")
//            ),
//            totalPages = 5,
//            beginningBalance = 500.asCurrency(),
//            endingBalance = (-500).asCurrency()
//        )
//
//        val BANK_STATEMENT_3 = newBankStatement(filename = "test2").update(
//            transactions = mutableListOf(
//                BASIC_TH_RECORD,
//                newHistoryRecord(checkNumber = 1001, description = "Check")
//            ),
//            totalPages = 5,
//            beginningBalance = 500.asCurrency(),
//            endingBalance = (-500).asCurrency()
//        )
//
//        val PAGE_METADATA_3_1 = PageMetadata(BATES_STAMP + "31", "test3", 1)
//        val CHECK_DATA_KEY_1000 = CheckDataKey(ACCOUNT_NUMBER, 1000)
//        val CHECK_DATA_1000 = CheckData(ACCOUNT_NUMBER, 1000, "test", FIXED_TRANSACTION_DATE, (-500).asCurrency(), PAGE_METADATA_3_1)
//        val PAGE_METADATA_3_2 = PageMetadata(BATES_STAMP + "32", "test3", 2)
//        val CHECK_DATA_KEY_1001 = CheckDataKey(ACCOUNT_NUMBER, 1001)
//        val CHECK_DATA_1001 = CheckData(ACCOUNT_NUMBER, 1001, "test", FIXED_TRANSACTION_DATE, (-500).asCurrency(), PAGE_METADATA_3_2)
//
//        val BANK_STATEMENT_2_WITH_CHECK_DATA = BANK_STATEMENT_2.copy(
//            transactions = mutableListOf(
//                BASIC_TH_RECORD,
//                newHistoryRecord(checkNumber = 1000, description = "Check", checkData = CHECK_DATA_1000)
//            ),
//        )
//
//        val BANK_STATEMENT_3_WITH_CHECK_DATA = BANK_STATEMENT_3.copy(
//            transactions = mutableListOf(
//                BASIC_TH_RECORD,
//                newHistoryRecord(checkNumber = 1001, description = "Check", checkData = CHECK_DATA_1001)
//            ),
//        )
//    }

    @Test
    fun testContent() {
        val data = "{\"bankIdentifier\":\"CITI® / AADVANTAGE® GOLD CARD\",\"statementDate\":1686088800000,\"pageNum\":null,\"totalPages\":null,\"summaryOfAccountsTable\":null,\"transactionTableDepositWithdrawal\":null,\"batesStamp\":\"MH - 002472\",\"accountNumber\":\"9652\",\"beginningBalance\":7164.29,\"endingBalance\":2644.81,\"transactionTableAmount\":null,\"transactionTableCreditsCharges\":null,\"transactionTableDebits\":null,\"transactionTableCredits\":null,\"transactionTableChecks\":null,\"interestCharged\":0.00,\"feesCharged\":0.00,\"pageMetadata\":{\"name\":\"CC - Molly - Citi x9652 Stmt (2023.05.06 - 2023.06.07)MH-002472-002475[1]\",\"page\":1,\"classification\":\"CITI CC\"}}"
        println(data.length)
        println(data.toByteArray(charset = Charsets.US_ASCII).size)


        val otherData = "{\"bankIdentifier\":\"www.citicards.com\",\"statementDate\":null,\"pageNum\":4,\"totalPages\":4,\"summaryOfAccountsTable\":null,\"transactionTableDepositWithdrawal\":null,\"batesStamp\":\"MH - 002475\",\"accountNumber\":null,\"beginningBalance\":null,\"endingBalance\":null,\"transactionTableAmount\":{\"records\":[{\"date\":\"06/05\",\"description\":\"carters, Inc. Atlanta GA\",\"amount\":93.49},{\"date\":\"06/05\",\"description\":\"EDGE FLORAL EVENT DESI NORTH BETHESD MD\",\"amount\":150.00},{\"date\":\"06/05\",\"description\":\"ONSTAR DATA PLAN-AT&T DALLAS TX\",\"amount\":15.00},{\"date\":\"06/06\",\"description\":\"AMZN Mktp US*P593R4US3 Amzn.com/bill WA\",\"amount\":28.59},{\"date\":\"06/06\",\"description\":\"SQ *ZOHRA SALON Rockville MD\",\"amount\":175.00},{\"date\":\"06/07\",\"description\":\"DD DOORDASH VIEDEFRAN 8559731040 CA\",\"amount\":15.08},{\"date\":\"06/07\",\"description\":\"DD DOORDASH DUNKIN 8559731040 CA\",\"amount\":16.85},{\"date\":\"06/07\",\"description\":\"DD DOORDASH MCDONALDS 8559731040 CA\",\"amount\":25.35},{\"date\":\"06/07\",\"description\":\"DD DOORDASH BURGERKIN 8559731040 CA\",\"amount\":51.85},{\"date\":\"06/07\",\"description\":\"PERSONALIZATION MALL 630-910-6000 IL\",\"amount\":104.85},{\"date\":\"06/07\",\"description\":\"AMZN Mktp US*RT01J5183 Amzn.com/bill WA\",\"amount\":171.75},{\"date\":\"06/07\",\"description\":\"DD DOORDASH BALDUCCIS 8559731040 CA\",\"amount\":53.35}]},\"transactionTableCreditsCharges\":null,\"transactionTableDebits\":null,\"transactionTableCredits\":null,\"transactionTableChecks\":null,\"interestCharged\":0.00,\"feesCharged\":0.00,\"pageMetadata\":{\"name\":\"CC - Molly - Citi x9652 Stmt (2023.05.06 - 2023.06.07)MH-002472-002475[4]\",\"page\":1,\"classification\":\"CITI CC\"}}"
        println(otherData.length)
        println(otherData.toByteArray().size)
    }
}