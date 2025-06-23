package com.goldberg.law.function.activity

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.*
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
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
        val output = ProcessDataModelActivity(dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, ClassifiedPdfMetadata("newpage.pdf", 1, DocumentType.CheckTypes.EAGLE_BANK_CHECK)), context)
        assertThat(output.isCheckModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(ClassifiedPdfMetadata("newpage.pdf", 1, DocumentType.CheckTypes.EAGLE_BANK_CHECK))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.checkDataModel!!))
    }

    @Test
    @Ignore
    fun testFakeStatementModel() {
        val output = ProcessDataModelActivity(dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, ClassifiedPdfMetadata("newpage.pdf", 2, DocumentType.BankTypes.WF_BANK)), context)
        assertThat(output.isStatementModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(ClassifiedPdfMetadata("newpage.pdf", 2, DocumentType.BankTypes.WF_BANK))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.statementDataModel!!))
    }

    @Test
    @Ignore
    fun testFakeExtraPageModel() {
        val output = ProcessDataModelActivity(dataExtractor, dataManager).processDataModel(
            ProcessDataModelActivityInput("1234", CLIENT_NAME, ClassifiedPdfMetadata("newpage.pdf", 6, DocumentType.IrrelevantTypes.EXTRA_PAGES)), context)
        assertThat(output.isExtraPageDataModel()).isTrue()
        assertThat(output.getDocumentDataModel().pageMetadata).isEqualTo(ClassifiedPdfMetadata("newpage.pdf", 6, DocumentType.IrrelevantTypes.EXTRA_PAGES))
        assertThat(dataManager.saveModel(CLIENT_NAME, output.extraPageDataModel!!))
    }
}