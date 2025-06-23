package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.document.model.ModelValues.newPdfMetadata
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import org.mockito.Mock

class PutDocumentClassificationFunctionTest {
    @Mock
    private val dataManager: AzureStorageDataManager = mock()

    private val putDocumentClassificationFunction = PutDocumentClassificationFunction(dataManager)

    @BeforeEach
    fun setup() {
        whenever(dataManager.loadDocumentClassification(any(), any())).thenReturn(LOADED_PDF_METADATA)
    }

    @AfterEach
    fun verify() {
        verifyNoMoreInteractions(dataManager)
    }

    @Test
    fun testOverwriteIndividualClassificationInvalid() {
        assertThrows<RuntimeException> {
            putDocumentClassificationFunction.overwriteClassificationIndividual(CLIENT_NAME, listOf(
                newPdfMetadata(pages = setOf(1,2, 3)),
                newPdfMetadata(filename = "something else", pages = setOf(1,2, 3)),
            ))
        }
    }

    @Test
    fun testOverwriteIndividualClassificationInvalidEmpty() {
        assertThrows<RuntimeException> {
            putDocumentClassificationFunction.overwriteClassificationIndividual(CLIENT_NAME, listOf())
        }
    }

    @Test
    fun testOverwriteIndividualClassificationNoChange() {
        val metadata = putDocumentClassificationFunction.overwriteClassificationIndividual(CLIENT_NAME, listOf(
            newPdfMetadata(pages = setOf(1,2)),
            newPdfMetadata(pages = setOf(3)),
        ))

        assertThat(metadata).isEqualTo(LOADED_PDF_METADATA)

        verify(dataManager).loadDocumentClassification(CLIENT_NAME, FILENAME)
    }

    @Test
    fun testOverwriteIndividualClassificationOverwrite() {
        val metadata = putDocumentClassificationFunction.overwriteClassificationIndividual(CLIENT_NAME, listOf(
            newPdfMetadata(pages = setOf(1,2, 3)),
        ))

        assertThat(metadata).isEqualTo(listOf(
            newPdfMetadata(pages = setOf(1, 2, 3)),
            newPdfMetadata(pages = setOf(4, 5, 6)),
        ))

        verify(dataManager).loadDocumentClassification(CLIENT_NAME, FILENAME)
        verify(dataManager).saveClassificationData(CLIENT_NAME, FILENAME, metadata)
    }

    @Test
    fun testOverwriteAllClassificationOverwrite() {
        val metadata = putDocumentClassificationFunction.overwriteClassification(CLIENT_NAME, listOf(
            newPdfMetadata(pages = setOf(1,2, 3)),
        ))

        assertThat(metadata).isEqualTo(listOf(
            newPdfMetadata(pages = setOf(1, 2, 3)),
        ))

        verify(dataManager).saveClassificationData(CLIENT_NAME, FILENAME, metadata)
    }

    @Test
    fun testOverwriteAllClassificationInvalid() {
        assertThrows<RuntimeException> {
            putDocumentClassificationFunction.overwriteClassification(CLIENT_NAME, listOf(
                newPdfMetadata(pages = setOf(1,2, 3)),
                newPdfMetadata(filename = "something else", pages = setOf(1,2, 3)),
            ))
        }
    }

    @Test
    fun testOverwriteAllClassificationInvalidEmpty() {
        assertThrows<RuntimeException> {
            putDocumentClassificationFunction.overwriteClassification(CLIENT_NAME, listOf())
        }
    }

    companion object {
        val LOADED_PDF_METADATA = listOf(
            newPdfMetadata(pages = setOf(1, 2)),
            newPdfMetadata(pages = setOf(3)),
            newPdfMetadata(pages = setOf(4, 5, 6)),
        )
    }
}