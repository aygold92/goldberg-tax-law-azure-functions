package com.goldberg.law.pdf.model

import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PdfDocumentTest {

    @Test
    fun testGeneratedRangeForPages() {
        assertThat(PdfDocument("Test", PD_DOCUMENT_1_PAGE).pages)
            .isEqualTo(listOf(1))
        assertThat(PdfDocument("Test", PD_DOCUMENT_3_PAGE).pages)
            .isEqualTo(listOf(1,2,3))
    }

    @Test
    fun testValidState() {
        PdfDocument("Test", PD_DOCUMENT_1_PAGE, listOf(5))
        PdfDocument("Test", PD_DOCUMENT_3_PAGE, listOf(1,3,5))
    }

    @Test
    fun testInvalidState() {
        assertThrows<IllegalStateException> { PdfDocument("Test", PD_DOCUMENT_1_PAGE, listOf(2, 5)) }
        assertThrows<IllegalStateException> { PdfDocument("Test", PD_DOCUMENT_3_PAGE, listOf(1,5)) }
        assertThrows<IllegalStateException> { PdfDocument("Test", PD_DOCUMENT_3_PAGE, listOf(1,3,5,7)) }
    }

    @Test
    fun testClassifiedType() {
        val originalDocument = PdfDocument("Test", PD_DOCUMENT_3_PAGE, listOf(2,3,5))
        val classifiedType = originalDocument.withType("test")

        assertThat(classifiedType.name).isEqualTo("Test")
        assertThat(classifiedType.document).isEqualTo(originalDocument.document)
        assertThat(classifiedType.pages).isEqualTo(listOf(2,3,5))
        assertThat(classifiedType.classification).isEqualTo("test")
    }

    companion object {
        val PD_DOCUMENT_1_PAGE =  PDDocument().apply { addPage(PDPage()) }
        val PD_DOCUMENT_3_PAGE =  PDDocument().apply {
            addPage(PDPage())
            addPage(PDPage())
            addPage(PDPage())
        }
    }
}