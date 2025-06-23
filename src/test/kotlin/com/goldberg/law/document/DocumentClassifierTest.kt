package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails
import com.azure.ai.documentintelligence.models.AnalyzeResult
import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.azure.core.util.polling.PollResponse
import com.azure.core.util.polling.SyncPoller
import com.goldberg.law.document.model.ModelValues.newPdfDocumentMulti
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.B_OF_A_CHECK
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC
import com.goldberg.law.document.model.pdf.DocumentType.IrrelevantTypes.EXTRA_PAGES
import com.goldberg.law.document.model.pdf.DocumentType.TransactionTypes.TRANSACTIONS_TYPE
import com.goldberg.law.util.GSON
import com.goldberg.law.util.readJson
import com.nhaarman.mockitokotlin2.any
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock


class DocumentClassifierTest {

    @Mock
    private val client: DocumentIntelligenceClient = mock()

    @Mock
    private val poller: SyncPoller<AnalyzeOperationDetails, AnalyzeResult> = mock()

    @Mock
    private val pollResponse: PollResponse<AnalyzeOperationDetails> = mock()

    @Mock
    private val analyzeResult: AnalyzeResult = mock()

    private val modelId = "Test"
    private val classifier = DocumentClassifier(client, modelId)

    @BeforeEach
    fun setup() {
        whenever(client.beginClassifyDocument(any(), any())).thenReturn(poller)
        whenever(poller.waitForCompletion()).thenReturn(pollResponse)
        whenever(poller.finalResult).thenReturn(analyzeResult)
    }

    @Test
    fun testNone() {
        val document = newPdfDocumentMulti(pages = (1..2).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            TRANSACTIONS_TYPE,
            EXTRA_PAGES,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf<ClassifiedPdfDocument>())
    }

    @Test
    fun testClassifyBasic() {
        val document = newPdfDocumentMulti(pages = (1..2).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            WF_BANK,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(1)).asClassifiedDocument(WF_BANK),
        ))
    }

    @Test
    fun testClassifyBasicExtraPages() {
        val document = newPdfDocumentMulti(pages = (1..4).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            EXTRA_PAGES,
            TRANSACTIONS_TYPE,
            WF_BANK,
            EXTRA_PAGES,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(3)).asClassifiedDocument(WF_BANK),
        ))
    }

    @Test
    fun testClassify() {
        val document = newPdfDocumentMulti(pages = (1..8).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            WF_BANK,
            TRANSACTIONS_TYPE,
            TRANSACTIONS_TYPE,
            EXTRA_PAGES,
            B_OF_A_CC,
            TRANSACTIONS_TYPE,
            B_OF_A_CC,
            B_OF_A_CC,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(1, 2, 3)).asClassifiedDocument(WF_BANK),
            document.docForPages(setOf(5, 6)).asClassifiedDocument(B_OF_A_CC),
            document.docForPages(setOf(7)).asClassifiedDocument(B_OF_A_CC),
            document.docForPages(setOf(8)).asClassifiedDocument(B_OF_A_CC),
        ))
    }

    @Test
    fun testChecks() {
        val document = newPdfDocumentMulti(pages = (1..4).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            B_OF_A_CHECK,
            B_OF_A_CHECK,
            B_OF_A_CHECK,
            B_OF_A_CHECK,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(1)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(2)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(3)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(4)).asClassifiedDocument(B_OF_A_CHECK),
        ))
    }

    @Test
    fun testClassifyStatementsAndChecks() {
        val document = newPdfDocumentMulti(pages = (1..4).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            B_OF_A,
            B_OF_A_CHECK,
            B_OF_A_CHECK,
            B_OF_A,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(1)).asClassifiedDocument(B_OF_A),
            document.docForPages(setOf(2)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(3)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(4)).asClassifiedDocument(B_OF_A),
        ))
    }

    @Test
    fun testWeird() {
        val document = newPdfDocumentMulti(pages = (1..10).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            B_OF_A,
            EXTRA_PAGES,
            TRANSACTIONS_TYPE,
            B_OF_A_CHECK,
            TRANSACTIONS_TYPE,
            EXTRA_PAGES,
            B_OF_A_CHECK,
            TRANSACTIONS_TYPE,
            EXTRA_PAGES,
            B_OF_A,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(listOf(
            document.docForPages(setOf(1, 3)).asClassifiedDocument(B_OF_A),
            document.docForPages(setOf(4)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(7)).asClassifiedDocument(B_OF_A_CHECK),
            document.docForPages(setOf(10)).asClassifiedDocument(B_OF_A),
        ))
    }

    companion object {
        fun newAnalyzedDocs(vararg docTypes: String) = docTypes.map { docType ->
            """ {"documentType":"$docType"} """.trim().readJson(AnalyzedDocument::class.java)
        }
    }
}