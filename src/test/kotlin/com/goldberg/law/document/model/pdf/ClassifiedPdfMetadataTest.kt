package com.goldberg.law.document.model.pdf

import com.goldberg.law.util.OBJECT_MAPPER
import com.nimbusds.jose.shaded.gson.Gson
import org.assertj.core.api.AssertionsForClassTypes.assertThat
import org.junit.jupiter.api.Test

class ClassifiedPdfMetadataTest {
    @Test
    fun testGSON() {
        val metadata = Gson().fromJson(METADATA_STRING, ClassifiedPdfMetadata::class.java)
        assertThat(metadata.filename).isEqualTo("test")
        assertThat(metadata.pages).isEqualTo(setOf(2,3,4))
        assertThat(metadata.pagesOrdered).isEqualTo(listOf(2,3,4))
        assertThat(metadata.getPageRange()).isEqualTo(Pair(2, 4))
        assertThat(metadata.classification).isEqualTo(DocumentType.BankTypes.WF_BANK)
        assertThat(metadata.documentType).isEqualTo(DocumentType.BANK)
    }

    @Test
    fun testJackson() {
        val metadata = OBJECT_MAPPER.readValue(METADATA_STRING, ClassifiedPdfMetadata::class.java)
        assertThat(metadata.filename).isEqualTo("test")
        assertThat(metadata.pages).isEqualTo(setOf(2,3,4))
        assertThat(metadata.pagesOrdered).isEqualTo(listOf(2,3,4))
        assertThat(metadata.getPageRange()).isEqualTo(Pair(2, 4))
        assertThat(metadata.classification).isEqualTo(DocumentType.BankTypes.WF_BANK)
        assertThat(metadata.documentType).isEqualTo(DocumentType.BANK)
    }

    companion object {
        val METADATA_STRING = """
            {"filename": "test", "pages": [4,3,2], "classification": "WF Bank"}
        """.trimIndent()
    }
}