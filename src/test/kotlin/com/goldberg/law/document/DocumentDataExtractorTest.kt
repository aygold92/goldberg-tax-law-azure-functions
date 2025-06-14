package com.goldberg.law.document

import com.azure.ai.formrecognizer.documentanalysis.DocumentAnalysisClient
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzeResult
import com.azure.ai.formrecognizer.documentanalysis.models.AnalyzedDocument
import com.azure.ai.formrecognizer.documentanalysis.models.OperationResult
import com.azure.core.util.polling.PollResponse
import com.azure.core.util.polling.SyncPoller
import com.fasterxml.jackson.databind.ObjectMapper
import com.goldberg.law.document.model.ModelValues.CHECK_FILENAME
import com.goldberg.law.document.model.ModelValues.newClassifiedPdfDocument
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_NFCU_SAME
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_WF_BANK_0
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_WF_BANK_1
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_WF_BANK_2
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_WF_BANK_3
import com.goldberg.law.document.model.StatementModelValues.Companion.STATEMENT_MODEL_WF_BANK_4
import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.tables.CheckEntriesTable
import com.goldberg.law.document.model.input.tables.CheckEntriesTableRow
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.util.asCurrency
import com.goldberg.law.util.normalizeDate
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import java.nio.file.Files
import java.nio.file.Paths

class DocumentDataExtractorTest {
    @Mock
    val client: DocumentAnalysisClient = mock()
    @Mock
    val poller: SyncPoller<OperationResult, AnalyzeResult> = mock()
    @Mock
    val operationResult: PollResponse<OperationResult> = mock()
    @Mock
    val analyzeResult: AnalyzeResult = mock()

    val documentDataExtractor = DocumentDataExtractor(client, STATEMENTS_MODEL_ID, CHECKS_MODEL_ID)

    @BeforeEach
    fun before() {
        whenever(client.beginAnalyzeDocument(any(), any())).thenReturn(poller)
        whenever(poller.waitForCompletion()).thenReturn(operationResult)
        whenever(poller.finalResult).thenReturn(analyzeResult)
    }

    @Test
    fun testNFCU() {
        val page = readFileRelative("NFCU_Same.json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.NFCU_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_NFCU_SAME)
    }

    @Test
    fun testNFCU_EOY() {
        val page = readFileRelative("NFCU_EOY_Date.json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.NFCU_BANK))
        assertThat(result.date).isEqualTo("1/14/2024")
    }

    @Test
    fun testModelWFBank0() {
        val page = readFileRelative("WFBank[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.WF_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_WF_BANK_0)
    }

    @Test
    fun testModelWFBank1() {
        val page = readFileRelative("WFBank[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(page = 2, filename = StatementModelValues.FileNames.WF_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_WF_BANK_1)
    }

    @Test
    fun testModelWFBank2() {
        val page = readFileRelative("WFBank[2].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(page = 3, filename = StatementModelValues.FileNames.WF_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_WF_BANK_2)
    }

    @Test
    fun testModelWFBank3() {
        val page = readFileRelative("WFBank[3].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(page = 5, filename = StatementModelValues.FileNames.WF_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_WF_BANK_3)
    }

    @Test
    fun testModelWFBank4() {
        val page = readFileRelative("WFBank[4].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(page = 6, filename = StatementModelValues.FileNames.WF_BANK))
        assertThat(result).isEqualTo(STATEMENT_MODEL_WF_BANK_4)
    }

    @Test
    fun testDoubleCash0() {
        val page = readFileRelative("DoubleCash[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(StatementModelValues.FileNames.DOUBLE_CASH))
        assertThat(result).isEqualTo(StatementModelValues.DOUBLE_CASH_0)
    }

    @Test
    fun testDoubleCash1() {
        val page = readFileRelative("DoubleCash[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(page = 2))
        assertThat(result).isEqualTo(StatementModelValues.DOUBLE_CASH_1)
    }

    @Test
    fun testC1Venture0() {
        val page = readFileRelative("C1Venture[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.C1_VENTURE))
        assertThat(result).isEqualTo(StatementModelValues.C1_VENTURE_0)
    }

    @Test
    fun testC1Venture1() {
        val page = readFileRelative("C1Venture[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.C1_VENTURE, page = 3))
        assertThat(result).isEqualTo(StatementModelValues.C1_VENTURE_1)
    }

    @Test
    fun testModelEagleBank0() {
        val page = readFileRelative("EagleBank[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 6))
        assertThat(result).isEqualTo(StatementModelValues.EAGLE_BANK_0)
    }

    @Test
    fun testModelEagleBank1() {
        val page = readFileRelative("EagleBank[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 7))
        assertThat(result).isEqualTo(StatementModelValues.EAGLE_BANK_1)
    }

    @Test
    fun testModelEagleBank2() {
        val page = readFileRelative("EagleBank[2].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 8))
        assertThat(result).isEqualTo(StatementModelValues.EAGLE_BANK_2)
    }

    @Test
    fun testModelEagleBank3() {
        val page = readFileRelative("EagleBank[3].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 9))
        assertThat(result).isEqualTo(StatementModelValues.EAGLE_BANK_3)
    }

    @Test
    fun testModelCitiEndOfYear0() {
        val page = readFileRelative("CitiEndOfYear[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(StatementModelValues.FileNames.CITI_EOY))
        assertThat(result).isEqualTo(StatementModelValues.CITI_END_OF_YEAR_0)
    }

    @Test
    fun testModelCitiEndOfYear1() {
        val page = readFileRelative("CitiEndOfYear[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.CITI_EOY, page = 2))
        assertThat(result).isEqualTo(StatementModelValues.CITI_END_OF_YEAR_1)
    }

    @Test
    fun testModelCitiEndOfYear2() {
        val page = readFileRelative("CitiEndOfYear[2].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.CITI_EOY, page = 3))
        assertThat(result).isEqualTo(StatementModelValues.CITI_END_OF_YEAR_2)
    }

    @Test
    fun testModelWFJoint0() {
        val page = readFileRelative("WFJoint[0].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.WF_JOINT))
        assertThat(result).isEqualTo(StatementModelValues.WF_JOINT_0)
    }

    @Test
    fun testModelWFJoint1() {
        val page = readFileRelative("WFJoint[1].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.WF_JOINT, page = 2))
        assertThat(result).isEqualTo(StatementModelValues.WF_JOINT_1)
    }

    @Test
    fun testModelWFJoint2() {
        val page = readFileRelative("WFJoint[2].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.WF_JOINT, page = 3))
        assertThat(result).isEqualTo(StatementModelValues.WF_JOINT_2)
    }

    @Test
    fun testModelWFJoint3() {
        val page = readFileRelative("WFJoint[3].json")
        whenever(analyzeResult.documents).thenReturn(listOf(OBJECT_MAPPER.readValue(page, AnalyzedDocument::class.java)))
        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.WF_JOINT, page = 5))
        assertThat(result).isEqualTo(StatementModelValues.WF_JOINT_3)
    }

    @Test
    fun testExtractDouble() {
        val page1 = readFileRelative("SandySpringCurrency.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 1))
        assertThat(result.endingBalance).isEqualTo(1532.36.asCurrency())
    }

    @Test
    fun testDateIsWeird() {
        val page1 = readFileRelative("SandySpringDate.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = StatementModelValues.FileNames.EAGLE_BANK, page = 1))
        assertThat(result.date).isEqualTo(normalizeDate("2 28 2022"))
        assertThat(result.pageNum).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(3)
    }

    @Test
    fun testSlashInTotalPages() {
        val page1 = readFileRelative("SlashInTotalPages.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = "amex", page = 1))
        assertThat(result.pageNum).isEqualTo(3)
        assertThat(result.totalPages).isEqualTo(12)
    }

    @Test
    fun testSlashAndExtraInTotalPages() {
        val page1 = readFileRelative("SlashAndExtraInTotalPages.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractStatementData(newClassifiedPdfDocument(filename = "amex", page = 1))
        assertThat(result.pageNum).isEqualTo(5)
        assertThat(result.totalPages).isEqualTo(12)
    }

    @Test
    fun testCheck() {
        val page1 = readFileRelative("EagleCheck.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractCheckData(newClassifiedPdfDocument(CHECK_FILENAME, 4, CheckTypes.EAGLE_BANK_CHECK))

        assertThat(result).isEqualTo(CheckDataModel(
            accountNumber = "100002492",
            checkNumber = 3079,
            to = "Czavine Remigio",
            description = "Mondays - June 21",
            date = normalizeDate("12 24 2020"),
            amount = 3600.asCurrency(),
            null,
            batesStamp = "MH-002017",
            pageMetadata = PdfDocumentPageMetadata(CHECK_FILENAME, 4, CheckTypes.EAGLE_BANK_CHECK),
        ))
    }

    @Test
    fun testCheckWithCheckEntries() {
        val page1 = readFileRelative("CheckWithCheckEntries.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractCheckData(newClassifiedPdfDocument(CHECK_FILENAME, 4, CheckTypes.NFCU_CHECK))

        assertThat(result).isEqualTo(CheckDataModel(
            accountNumber = null,
            checkNumber = null,
            to = null,
            description = null,
            date = null,
            amount = null,
            checkEntries = CheckEntriesTable(images = listOf(
                CheckEntriesTableRow(accountNumber = "1663", description = null, amount = 20000.asCurrency(), to = "Bank of America", checkNumber = 1, date = "2/29/2024"),
                CheckEntriesTableRow(accountNumber = "1663", description = null, amount = 10000.asCurrency(), to = "Citi BANK", checkNumber = 3, date = "3/5/2024"),
                CheckEntriesTableRow(accountNumber = "1663", description = null, amount = 9550.asCurrency(), to = "Cinci R. Brandt Esq.", checkNumber = 2, date = "3/6/2024"),
                CheckEntriesTableRow(accountNumber = "1663", description = null, amount = 10000.asCurrency(), to = "Citi BANK", checkNumber = 4, date = "3/29/2024"),
            )),
            batesStamp = null,
            pageMetadata = PdfDocumentPageMetadata(CHECK_FILENAME, 4, CheckTypes.NFCU_CHECK),
        ))
    }

    @Test
    fun testCheckWithAccountNumberCheckNumberValue() {
        val page1 = readFileRelative("Check_AccountNumberCheckNumber.json")

        whenever(analyzeResult.documents).thenReturn(
            listOf(OBJECT_MAPPER.readValue(page1, AnalyzedDocument::class.java)),
        )

        val result = documentDataExtractor.extractCheckData(newClassifiedPdfDocument(CHECK_FILENAME, 4, CheckTypes.MISC_CHECK))

        assertThat(result).isEqualTo(CheckDataModel(
            accountNumber = "8558",
            checkNumber = 5563,
            to = "Cash",
            description = null,
            date = normalizeDate("1 15 2022"),
            amount = 2000.asCurrency(),
            null,
            batesStamp = null,
            pageMetadata = PdfDocumentPageMetadata(CHECK_FILENAME, 4, CheckTypes.MISC_CHECK),
        ))
    }

    companion object {
        val PATH_TO_CLASS = "/" + DocumentDataExtractor::class.java.packageName.replace('.', '/')

        private val OBJECT_MAPPER = ObjectMapper()
        fun readFileRelative(filename: String): String =
            Files.readString(Paths.get(javaClass.getResource("$PATH_TO_CLASS/extraction/$filename")!!.toURI()))

        private val STATEMENTS_MODEL_ID = "TestModel"
        private val CHECKS_MODEL_ID = "ChecksModel"
    }
}