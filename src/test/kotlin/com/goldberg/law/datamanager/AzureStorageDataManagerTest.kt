package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.goldberg.law.document.model.StatementModelValues.newCheckDataModel
import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLIENT_ID
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.util.toStringDetailed
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.verify
import org.mockito.kotlin.verifyNoMoreInteractions
import org.mockito.kotlin.whenever

class AzureStorageDataManagerTest {
    @Mock
    val serviceClient: BlobServiceClient = mock()
    @Mock
    val containerClient: BlobContainerClient = mock()

    @Mock
    val blobClient: BlobClient = mock()

    val dataManager = AzureStorageDataManager(serviceClient)

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
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(newStatementModel().toStringDetailed()))
        val result = dataManager.loadModel(newClassification())

        assertThat(result).isEqualTo(newStatementModel())

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun testLoadCheckModel() {
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(newCheckDataModel().toStringDetailed()))
        val result = dataManager.loadModel(newClassification(type = DocumentType.CheckTypes.MISC_CHECK))

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun testLoadExtraPageModel() {
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(ExtraPageDataModel(newClassification(type = DocumentType.IrrelevantTypes.EXTRA_PAGES)).toStringDetailed()))
        val result = dataManager.loadModel(newClassification(type = DocumentType.IrrelevantTypes.EXTRA_PAGES))

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }
}