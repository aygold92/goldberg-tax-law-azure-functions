package com.goldberg.law.document.model.pdf

import com.azure.core.util.BinaryData
import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.function.model.InputFileMetadata
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.withoutExtension
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

open class PdfDocumentPage(val fileName: String, private val document: PDDocument, val page: Int, val metadata: InputFileMetadata? = null) {
    private val logger = KotlinLogging.logger {}

    constructor(pdfPageData: PdfPageData, document: PDDocument): this(pdfPageData.fileName, document, pdfPageData.page)

    init {
        if (document.numberOfPages != 1) {
            throw IllegalStateException("Invalid state for PDF document '$fileName': underlying doc has ${document.numberOfPages} pages but should always be a single page")
        }
    }

    private fun documentName() = fileName.withoutExtension()
    fun nameWithPage() = "${documentName()}[$page]"
    fun fileNameWithPage() = "${nameWithPage()}.pdf"
    fun modelFileName() = "${documentName()}/${nameWithPage()}_Model.json"
    fun splitPageFilePath() = "${documentName()}/${fileNameWithPage()}"
    fun pdfPage() = PdfPageData(fileName, page)

    // some sharing inside PDFBox requires this to be synchronized
    fun toBinaryData(): BinaryData = synchronized(LOCK) {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        BinaryData.fromBytes(outputStream.toByteArray())
    }

    fun saveToFile(fileName: String) = document.save(fileName).let { fileName }

    fun withType(type: String): ClassifiedPdfDocumentPage {
        return ClassifiedPdfDocumentPage(fileName, document, page, type)
    }

    // for unit testing
    fun isDocEqual(other: PdfDocumentPage) = document == other.document

    override fun toString() = "{fileName: $fileName, page: $page}"

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is PdfDocumentPage) return false

        return other.fileName == this.fileName && other.page == this.page && isDocEqual(other)
    }

    override fun hashCode(): Int {
        var result = fileName.hashCode()
        result = 31 * result + document.hashCode()
        result = 31 * result + page
        return result
    }

    companion object {
        const val LOCK = "Lock"
    }
}