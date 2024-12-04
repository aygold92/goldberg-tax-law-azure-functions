package com.goldberg.law.document.model.pdf

import com.goldberg.law.function.model.PdfPageData
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class PdfDocumentMetadataPageTest {

    @Test
    fun testValidState() {
        val page = PdfDocumentPage("Test", PD_DOCUMENT_1_PAGE, 1)
        assertThat(page).isEqualTo(PdfDocumentPage(PdfPageData("Test", 1), PD_DOCUMENT_1_PAGE))
        assertThat(page.page).isEqualTo(1)
        assertThat(page.fileName).isEqualTo("Test")
        assertThat(page.pdfPage()).isEqualTo(PdfPageData("Test", 1))
        assertThat(page.nameWithPage()).isEqualTo("Test[1]")
        assertThat(page.fileNameWithPage()).isEqualTo("Test[1].pdf")
        assertThat(page.splitPageFilePath()).isEqualTo("Test/Test[1].pdf")
        assertThat(page.modelFileName()).isEqualTo("Test/Test[1]_Model.json")
    }

    @Test
    fun testInvalidState() {
        assertThrows<IllegalStateException> { PdfDocumentPage("Test", PD_DOCUMENT_2_PAGE, 1) }
    }

    @Test
    fun testClassifiedType() {
        val originalDocument = PdfDocumentPage("Test", PD_DOCUMENT_1_PAGE, 4)
        val classifiedType = originalDocument.withType("test")

        assertThat(classifiedType.fileName).isEqualTo("Test")
        assertThat(classifiedType.isDocEqual(originalDocument)).isTrue()
        assertThat(classifiedType.page).isEqualTo(4)
        assertThat(classifiedType.classification).isEqualTo("test")
    }

    companion object {
        val PD_DOCUMENT_1_PAGE =  PDDocument().apply { addPage(PDPage()) }
        val PD_DOCUMENT_2_PAGE =  PDDocument().apply {
            addPage(PDPage())
            addPage(PDPage())
        }
    }
}