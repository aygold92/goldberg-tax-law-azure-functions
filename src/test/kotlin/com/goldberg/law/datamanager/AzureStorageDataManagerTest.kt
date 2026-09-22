package com.goldberg.law.datamanager

import com.azure.core.util.BinaryData
import com.azure.storage.blob.BlobClient
import com.azure.storage.blob.BlobContainerClient
import com.azure.storage.blob.BlobServiceClient
import com.azure.storage.blob.options.BlobParallelUploadOptions
import com.goldberg.law.document.model.StatementModelValues.newCheckDataModel
import com.goldberg.law.document.model.StatementModelValues.newStatementModel
import com.goldberg.law.document.model.input.ExtraPageDataModel
import com.goldberg.law.document.model.pdf.DocumentType
import com.goldberg.law.entity.EntityValues.CLASSFN_ID
import com.goldberg.law.entity.EntityValues.CLIENT_ID
import com.goldberg.law.entity.EntityValues.newClassification
import com.goldberg.law.entity.EntityValues.newClassificationInfo
import com.goldberg.law.util.toStringDetailed
import java.nio.charset.StandardCharsets
import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock
import org.mockito.Mockito.mock
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.argumentCaptor
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

    val inputPdfCache: InputPdfCache = mock()
    val dataManager = AzureStorageDataManager(serviceClient, inputPdfCache)

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

        assertThat(result).isEqualTo(StoredModel.Azure(newStatementModel()))
        assertThat(result.asDocumentDataModel()).isEqualTo(newStatementModel())

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun testLoadCheckModel() {
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(newCheckDataModel().toStringDetailed()))
        val result = dataManager.loadModel(newClassification(type = DocumentType.CheckTypes.CHECKS))

        assertThat(result).isEqualTo(StoredModel.Azure(newCheckDataModel()))

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun testLoadExtraPageModel() {
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(ExtraPageDataModel(newClassification(type = DocumentType.ExtraPageTypes.TEXT)).toStringDetailed()))
        val result = dataManager.loadModel(newClassification(type = DocumentType.ExtraPageTypes.TEXT))

        assertThat(result).isInstanceOf(StoredModel.Azure::class.java)

        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun `a classification with an extraction session reads back as the agent's raw json, unparsed`() {
        val agentJson = """{"accounts":[{"account_number":"1234","payee":"Café Dupré"}],"schema_version":"v9"}"""
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString(agentJson))

        val result = dataManager.loadModel(newClassification(info = newClassificationInfo(extractionSessionId = "sess_extract_1")))

        // Verbatim: no schema class sits between the blob and the caller, so fields this app doesn't model survive
        assertThat(result).isEqualTo(StoredModel.Agent(agentJson))

        // Same blob the Azure pipeline uses
        verify(serviceClient).getBlobContainerClient(CLIENT_ID.toString())
        verify(containerClient).getBlobClient("${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID.json")
        verify(blobClient).downloadContent()
        verifyNoMoreInteractions(serviceClient, containerClient, blobClient)
    }

    @Test
    fun `an agent result can't be passed off as a DocumentDataModel`() {
        whenever(blobClient.downloadContent()).thenReturn(BinaryData.fromString("""{"accounts":[]}"""))

        val result = dataManager.loadModel(newClassification(info = newClassificationInfo(extractionSessionId = "sess_extract_1")))

        assertThatThrownBy { result.asDocumentDataModel() }.isInstanceOf(IllegalStateException::class.java)
    }

    @Test
    fun `agent output is stored at the classification's model location, as utf-8 bytes`() {
        val agentJson = """{"banks":{"bofa":{"name":"Café Dupré"}}}"""

        val location = dataManager.saveAgentOutput(newClassification(), agentJson)

        assertThat(location).isEqualTo(
            StorageLocation(CLIENT_ID.toString(), "${AzureStorageDataManager.MODEL_FILE_FOLDER}/$CLASSFN_ID", Extension.JSON)
        )

        val uploaded = argumentCaptor<BlobParallelUploadOptions>()
        verify(blobClient).uploadWithResponse(uploaded.capture(), anyOrNull(), anyOrNull())
        // US-ASCII would turn the accented characters into '?'
        val uploadedText = uploaded.firstValue.dataFlux.map { StandardCharsets.UTF_8.decode(it).toString() }.blockFirst()
        assertThat(uploadedText).isEqualTo(agentJson)
        assertThat(uploaded.firstValue.headers.contentType).isEqualTo("application/json")
    }
}
