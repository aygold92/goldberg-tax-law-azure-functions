package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.goldberg.law.document.model.ModelValues
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.StatementModelValues
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.util.toStringDetailed
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class AzureStorageDataManagerTest {
    @Mock
    val serviceClient: BlobServiceClient = mock()
    @Mock
    val containerClient: BlobContainerClient = mock()

    @Mock
    val blobClient: BlobClient = mock()

    @BeforeEach
    fun setup() {
        whenever(serviceClient.getBlobContainerClient(any())).thenReturn(containerClient)
        whenever(containerClient.getBlobClient(any())).thenReturn(blobClient)
    }

//    @Test
//    fun testFetchInputDocuments() {
//        val dataManager = AzureStorageDataManager(pdfSplitter, serviceClient, CONTAINER_NAMES)
//        whenever(splitInputContainerClient.listBlobs()).thenReturn(PagedIterable({EntityPaged()}))
//        val documents = dataManager.fetchInputPdfDocuments(setOf("Test", "Test2"))
//    }

    @Test
    fun testLoadStatementModel() {
        val dataManager = AzureStorageDataManager(serviceClient)
        val blobName = "Test"
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(StatementModelValues.STATEMENT_MODEL_WF_BANK_0.toStringDetailed()))
        val result = dataManager.loadModel(CLIENT_NAME, ClassifiedPdfMetadata(blobName, 1, DocumentType.BankTypes.WF_BANK))

        assertThat(result).isEqualTo(StatementModelValues.STATEMENT_MODEL_WF_BANK_0)

        verify(serviceClient).getBlobContainerClient(BlobContainer.MODELS.forClient(CLIENT_NAME))
        verify(containerClient).getBlobClient("$blobName/$blobName[1-1]_Model.json")
        verifyNoMoreInteractions(serviceClient)
    }

    @Test
    fun testLoadCheckModel() {
        val dataManager = AzureStorageDataManager(serviceClient)
        val blobName = "Test"

        val model = ModelValues.newCheckData(1000)
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(model.toStringDetailed()))
        val result = dataManager.loadModel(CLIENT_NAME, ClassifiedPdfMetadata(blobName, 1, DocumentType.CheckTypes.MISC_CHECK))

        assertThat(result).isEqualTo(model)
        verify(serviceClient).getBlobContainerClient(BlobContainer.MODELS.forClient(CLIENT_NAME))
        verify(containerClient).getBlobClient("$blobName/$blobName[1-1]_Model.json")
        verifyNoMoreInteractions(serviceClient)
    }

    @Test
    fun testLoadExtraPageModel() {
        val dataManager = AzureStorageDataManager(serviceClient)
        val blobName = "Test"

        val model = ExtraPageDataModel(ClassifiedPdfMetadata(blobName, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES))
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(model.toStringDetailed()))
        val result = dataManager.loadModel(CLIENT_NAME, ClassifiedPdfMetadata(blobName, 1, DocumentType.IrrelevantTypes.EXTRA_PAGES))

        assertThat(result).isEqualTo(model)
        verify(serviceClient).getBlobContainerClient(BlobContainer.MODELS.forClient(CLIENT_NAME))
        verify(containerClient).getBlobClient("$blobName/$blobName[1-1]_Model.json")
        verifyNoMoreInteractions(serviceClient)
    }
}