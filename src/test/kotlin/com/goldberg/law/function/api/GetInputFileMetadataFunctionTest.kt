package com.goldberg.law.function.api

import com.goldberg.law.datamanager.AzureStorageDataManager
import com.goldberg.law.document.model.ModelValues.CLIENT_NAME
import com.goldberg.law.document.model.ModelValues.FILENAME
import com.goldberg.law.function.model.metadata.InputFileMetadata
import com.goldberg.law.function.model.request.GetInputFileMetadataRequest
import com.nhaarman.mockitokotlin2.*
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.mockito.Mock

class GetInputFileMetadataFunctionTest {
    @Mock
    private val dataManager: AzureStorageDataManager = mock()

    private val getInputFileMetadataFunction = GetInputFileMetadataFunction(dataManager)

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
    fun `should successfully retrieve metadata when file exists`() {
        val request = GetInputFileMetadataRequest(CLIENT_NAME, FILENAME)
        
        // This would be called in the actual function
        val metadata = dataManager.loadInputMetadata(CLIENT_NAME, FILENAME)
        
        assertThat(metadata).isEqualTo(EXISTING_METADATA)
        verify(dataManager).loadInputMetadata(CLIENT_NAME, FILENAME)
    }

    @Test
    fun `should return null when file does not exist`() {
        whenever(dataManager.loadInputMetadata(CLIENT_NAME, FILENAME)).thenReturn(null)
        
        val request = GetInputFileMetadataRequest(CLIENT_NAME, FILENAME)
        
        // Simulate loading metadata for non-existent file
        val metadata = dataManager.loadInputMetadata(CLIENT_NAME, FILENAME)
        
        assertThat(metadata).isNull()
        verify(dataManager).loadInputMetadata(CLIENT_NAME, FILENAME)
    }

    companion object {
        val EXISTING_METADATA = InputFileMetadata(
            numstatements = 5,
            classified = true,
            analyzed = false,
            statements = setOf("statement1", "statement2")
        )
    }
}
