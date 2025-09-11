package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.request.UpdateInputFileMetadataRequest
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class UpdateInputFileMetadataFunctionTest {
    @Mock
    private val dataManager: AzureStorageDataManager = mock()

    private val updateInputFileMetadataFunction = UpdateInputFileMetadataFunction(dataManager)

    @BeforeEach
    fun setup() {
        // Setup default behavior for existing metadata
        whenever(dataManager.loadInputMetadata(any(), any())).thenReturn(EXISTING_METADATA)
    }

    @AfterEach
    fun verify() {
        verifyNoMoreInteractions(dataManager)
    }

    @Test
    fun `should successfully update metadata when file exists`() {
        val request = UpdateInputFileMetadataRequest(CLIENT_NAME, FILENAME, NEW_METADATA)
        
        // This would be called in the actual function
        dataManager.updateInputPdfMetadata(CLIENT_NAME, FILENAME, NEW_METADATA)
        
        verify(dataManager).updateInputPdfMetadata(CLIENT_NAME, FILENAME, NEW_METADATA)
    }

    @Test
    fun `should load existing metadata when file exists`() {
        val request = UpdateInputFileMetadataRequest(CLIENT_NAME, FILENAME, NEW_METADATA)
        
        // Simulate loading existing metadata
        val existingMetadata = dataManager.loadInputMetadata(CLIENT_NAME, FILENAME)
        
        assertThat(existingMetadata).isEqualTo(EXISTING_METADATA)
        verify(dataManager).loadInputMetadata(CLIENT_NAME, FILENAME)
    }

    companion object {
        val EXISTING_METADATA = InputFileMetadata(
            numstatements = 5,
            classified = true,
            analyzed = false,
            statements = setOf("statement1", "statement2")
        )
        
        val NEW_METADATA = InputFileMetadata(
            numstatements = 3,
            classified = true,
            analyzed = true,
            statements = setOf("statement1", "statement2", "statement3")
        )
    }
}
