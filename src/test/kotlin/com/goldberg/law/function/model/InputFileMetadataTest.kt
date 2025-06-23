package com.goldberg.law.function.model

import com.goldberg.law.function.model.metadata.InputFileMetadata
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class InputFileMetadataTest {
    @Test
    fun testToMap() {
        assertThat(InputFileMetadata(10, true, true, setOf("Test", "test2")).toMap())
            .isEqualTo(mapOf("numstatements" to "10", "classified" to "true", "analyzed" to "true", "statements" to "Test, test2"))
    }

    @Test
    fun testToMapNulls() {
        assertThat(InputFileMetadata(null, true, true, null).toMap())
            .isEqualTo(mapOf("classified" to "true", "analyzed" to "true"))
    }

    @Test
    fun testToMapDefault() {
        assertThat(InputFileMetadata().toMap())
            .isEqualTo(mapOf("classified" to "false", "analyzed" to "false"))
    }
}