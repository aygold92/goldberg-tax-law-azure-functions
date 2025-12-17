package com.goldberg.law.document

import com.azure.ai.documentintelligence.DocumentIntelligenceClient
import com.azure.ai.documentintelligence.models.AnalyzeOperationDetails
import com.azure.ai.documentintelligence.models.AnalyzeResult
import com.azure.ai.documentintelligence.models.AnalyzedDocument
import com.azure.core.util.polling.PollResponse
import com.azure.core.util.polling.SyncPoller
import com.goldberg.law.document.model.StatementModelValues.newPdfDocument
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.B_OF_A
import com.goldberg.law.document.model.pdf.DocumentType.BankTypes.WF_BANK
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.CHECKS
import com.goldberg.law.document.model.pdf.DocumentType.CheckTypes.CHECKS_RAW
import com.goldberg.law.document.model.pdf.DocumentType.CreditCardTypes.B_OF_A_CC
import com.goldberg.law.document.model.pdf.DocumentType.TransactionTypes.TRANSACTIONS_TYPE
import com.goldberg.law.entity.EntityValues.DEFAULT_CLASSIFICATION_TYPE
import com.goldberg.law.entity.EntityValues.newClassifiedFile
import com.goldberg.law.entity.EntityValues.newClassifiedPages
import com.goldberg.law.util.readJson
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.any
import org.mockito.Mockito.mock
import org.mockito.kotlin.whenever


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
        val document = newPdfDocument(pages = (1..2).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            TRANSACTIONS_TYPE,
            DocumentType.ExtraPageTypes.TEXT,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf()))
    }

    @Test
    fun testClassifyBasic() {
        val document = newPdfDocument(pages = (1..2).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            DEFAULT_CLASSIFICATION_TYPE,
            DEFAULT_CLASSIFICATION_TYPE,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(),
            newClassifiedPages(pages = setOf(2))
        )))
    }

    @Test
    fun testClassifyBasicExtraPages() {
        val document = newPdfDocument(pages = (1..4).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            DocumentType.ExtraPageTypes.TEXT,
            TRANSACTIONS_TYPE,
            WF_BANK,
            DocumentType.ExtraPageTypes.TEXT,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(pages = setOf(3), classification = WF_BANK),
        )))
    }

    @Test
    fun testClassifyStatementsWithTransactions() {
        val document = newPdfDocument(pages = (1..8).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            WF_BANK,
            TRANSACTIONS_TYPE,
            TRANSACTIONS_TYPE,
            DocumentType.ExtraPageTypes.TEXT,
            B_OF_A_CC,
            TRANSACTIONS_TYPE,
            B_OF_A_CC,
            B_OF_A_CC,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(pages = setOf(1, 2, 3), classification = WF_BANK),
            newClassifiedPages(pages = setOf(5, 6), classification = B_OF_A_CC),
            newClassifiedPages(pages = setOf(7), classification = B_OF_A_CC),
            newClassifiedPages(pages = setOf(8), classification = B_OF_A_CC),
        )))
    }

    @Test
    fun testChecks() {
        val document = newPdfDocument(pages = (1..5).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            CHECKS_RAW,
            CHECKS,
            CHECKS,
            CHECKS_RAW,
            CHECKS_RAW,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(pages = setOf(1), classification = CHECKS_RAW),
            newClassifiedPages(pages = setOf(2), classification = CHECKS),
            newClassifiedPages(pages = setOf(3), classification = CHECKS),
            newClassifiedPages(pages = setOf(4), classification = CHECKS_RAW),
            newClassifiedPages(pages = setOf(5), classification = CHECKS_RAW),
        )))
    }

    @Test
    fun testClassifyStatementsAndChecks() {
        val document = newPdfDocument(pages = (1..4).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            B_OF_A,
            CHECKS,
            CHECKS,
            B_OF_A,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(pages = setOf(2), classification = CHECKS),
            newClassifiedPages(pages = setOf(3), classification = CHECKS),
            newClassifiedPages(pages = setOf(1), classification = B_OF_A),
            newClassifiedPages(pages = setOf(4), classification = B_OF_A),
        )))
    }

    @Test
    fun testWeird() {
        val document = newPdfDocument(pages = (1..10).toSet())
        whenever(analyzeResult.documents).thenReturn(newAnalyzedDocs(
            B_OF_A,
            DocumentType.ExtraPageTypes.TEXT,
            TRANSACTIONS_TYPE,
            CHECKS,
            TRANSACTIONS_TYPE,
            DocumentType.ExtraPageTypes.TEXT,
            CHECKS,
            TRANSACTIONS_TYPE,
            DocumentType.ExtraPageTypes.TEXT,
            B_OF_A,
        ))
        val result = classifier.classifyDocument(document)
        assertThat(result).isEqualTo(newClassifiedFile(classifications = listOf(
            newClassifiedPages(pages = setOf(4), classification = CHECKS),
            newClassifiedPages(pages = setOf(7), classification = CHECKS),
            newClassifiedPages(pages = setOf(1, 3, 5, 8), classification = B_OF_A),
            newClassifiedPages(pages = setOf(10), classification = B_OF_A),
        )))
    }

    companion object {
        fun newAnalyzedDocs(vararg docTypes: String) = docTypes.map { docType ->
            """ {"documentType":"$docType"} """.trim().readJson(AnalyzedDocument::class.java)
        }
    }
}