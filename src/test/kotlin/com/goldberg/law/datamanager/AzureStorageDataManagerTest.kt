package com.goldberg.law.datamanager

import com.azure.core.http.rest.PagedIterable
import com.azure.core.http.rest.PagedResponse
import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.goldberg.law.StorageBlobContainersNames
import com.goldberg.law.document.PdfSplitter
import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.toStringDetailed
import com.nhaarman.mockitokotlin2.mock
import com.nhaarman.mockitokotlin2.whenever
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AzureStorageDataManagerTest {
    @Mock
    val serviceClient: BlobServiceClient = mock()


    @Mock
    val inputContainerClient: BlobContainerClient = mock()
    @Mock
    val splitInputContainerClient: BlobContainerClient = mock()
    @Mock
    val modelsContainerClient: BlobContainerClient = mock()
    @Mock
    val outputContainerClient: BlobContainerClient = mock()

    @Mock
    val blobClient: BlobClient = mock()

    @Mock
    val pdfSplitter: PdfSplitter = mock()

    @BeforeEach
    fun setup() {
        whenever(serviceClient.getBlobContainerClient("input")).thenReturn(inputContainerClient)
        whenever(serviceClient.getBlobContainerClient("splitInput")).thenReturn(splitInputContainerClient)
        whenever(serviceClient.getBlobContainerClient("models")).thenReturn(modelsContainerClient)
        whenever(serviceClient.getBlobContainerClient("output")).thenReturn(outputContainerClient)
    }

//    @Test
//    fun testFetchInputDocuments() {
//        val dataManager = AzureStorageDataManager(pdfSplitter, serviceClient, CONTAINER_NAMES)
//        whenever(splitInputContainerClient.listBlobs()).thenReturn(PagedIterable({EntityPaged()}))
//        val documents = dataManager.fetchInputPdfDocuments(setOf("Test", "Test2"))
//    }

    @Test
    fun testLoadStatementModel() {
        val dataManager = AzureStorageDataManager(pdfSplitter, serviceClient, CONTAINER_NAMES)
        val blobName = "Test"
        whenever(modelsContainerClient.getBlobClient("$blobName/$blobName[1]_Model.json")).thenReturn(blobClient)
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(StatementModelValues.STATEMENT_MODEL_WF_BANK_0.toStringDetailed()))
        val result = dataManager.loadModel(PdfPageData(blobName, 1))

        assertThat(result).isEqualTo(StatementModelValues.STATEMENT_MODEL_WF_BANK_0)
    }

    @Test
    fun testLoadCheckModel() {
        val dataManager = AzureStorageDataManager(pdfSplitter, serviceClient, CONTAINER_NAMES)
        val blobName = "Test"
        whenever(modelsContainerClient.getBlobClient("$blobName/$blobName[1]_Model.json")).thenReturn(blobClient)

        val model = ModelValues.newCheckData(1000)
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(model.toStringDetailed()))
        val result = dataManager.loadModel(PdfPageData(blobName, 1))

        assertThat(result).isEqualTo(model)
    }

    @Test
    fun testLoadExtraPageModel() {
        val dataManager = AzureStorageDataManager(pdfSplitter, serviceClient, CONTAINER_NAMES)
        val blobName = "Test"
        whenever(modelsContainerClient.getBlobClient("$blobName/$blobName[1]_Model.json")).thenReturn(blobClient)

        val model = ExtraPageDataModel(PdfDocumentPageMetadata(blobName, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES))
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(model.toStringDetailed()))
        val result = dataManager.loadModel(PdfPageData(blobName, 1))

        assertThat(result).isEqualTo(model)
    }

    companion object {
        val CONTAINER_NAMES = StorageBlobContainersNames("input", "splitInput", "models", "output")
    }
}