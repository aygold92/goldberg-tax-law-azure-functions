package com.goldberg.law.document.model.pdf

import com.goldberg.law.util.OBJECT_MAPPER
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class PdfMetadataTest {
    @Test
    fun testGSON() {
        val metadata = Gson().fromJson(METADATA_STRING, PdfMetadata::class.java)
        assertThat(metadata.filename).isEqualTo("test")
        assertThat(metadata.pages).isEqualTo(setOf(2,3,4))
        assertThat(metadata.pagesOrdered).isEqualTo(listOf(2,3,4))
        assertThat(metadata.getPageRange()).isEqualTo(Pair(2, 4))
    }

    @Test
    fun testJackson() {
        val metadata = OBJECT_MAPPER.readValue(METADATA_STRING, PdfMetadata::class.java)
        assertThat(metadata.filename).isEqualTo("test")
        assertThat(metadata.pages).isEqualTo(setOf(2,3,4))
        assertThat(metadata.pagesOrdered).isEqualTo(listOf(2,3,4))
        assertThat(metadata.getPageRange()).isEqualTo(Pair(2, 4))
    }

    companion object {
        val METADATA_STRING = """
            {"filename": "test", "pages": [4,3,2]}
        """.trimIndent()
    }
}