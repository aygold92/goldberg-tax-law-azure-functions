package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.*
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.function.model.activity.ProcessDataModelActivityInput
import com.microsoft.azure.functions.ExecutionContext
import com.nhaarman.mockitokotlin2.mock
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.mockito.Mock
import kotlin.test.Ignore

class ProcessDataModelActivityTest {
    @Mock
    private val dataManager: AzureStorageDataManager = mock()
    @Mock
    private val classifier: DocumentClassifier = mock()
    @Mock
    private val dataExtractor: DocumentDataExtractor = mock()
    @Mock
    val context: ExecutionContext = mock()

    /**
     * These tests are for the quick tests that don't hit the document intelligence service
     * TODO: make this more clean
     */
    @Test
    @Ignore
    fun testFakeCheckModel() {
        val output = ProcessDataModelActivity(classifier, dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, PdfPageData("newpage.pdf", 1), null), context)
        assertThat(output.isCheckModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(PdfDocumentPageMetadata("newpage.pdf", 1, DocumentType.CheckTypes.EAGLE_BANK_CHECK))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.checkDataModel!!))
    }

    @Test
    @Ignore
    fun testFakeStatementModel() {
        val output = ProcessDataModelActivity(classifier, dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, PdfPageData("newpage.pdf", 2), null), context)
        assertThat(output.isStatementModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(PdfDocumentPageMetadata("newpage.pdf", 2, DocumentType.BankTypes.WF_BANK))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.statementDataModel!!))
    }

    @Test
    @Ignore
    fun testFakeExtraPageModel() {
        val output = ProcessDataModelActivity(classifier, dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, PdfPageData("newpage.pdf", 6), null), context)
        assertThat(output.isExtraPageDataModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(PdfDocumentPageMetadata("newpage.pdf", 6, DocumentType.IrrelevantTypes.EXTRA_PAGES))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.extraPageDataModel!!))
    }
}