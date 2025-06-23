package com.goldberg.law.document.model.pdf

import com.azure.core.util.BinaryData
import com.goldberg.law.document.exception.InvalidPdfException
import com.goldberg.law.util.docForPages
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

open class PdfDocument(override val filename: String, private val document: PDDocument, override val pages: Set<Int> = (1..document.numberOfPages).toSet()): IPdfMetadata {
    val totalPages = document.numberOfPages
    val pagesOrdered = pages.sorted()

    init {
        if (totalPages != pages.size) {
            throw InvalidPdfException("Pdf document has $totalPages pages but is initialized with $pages (size: ${pages.size})")
        }
    }

    override fun firstPage(): Int = pagesOrdered.first()
    override fun lastPage(): Int = pagesOrdered.last()

    // some sharing inside PDFBox requires this to be synchronized
    fun toBinaryData(): BinaryData = synchronized(LOCK) {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        BinaryData.fromBytes(outputStream.toByteArray())
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is PdfDocument) return false

        return other.filename == this.filename && this.pages == other.pages && isDocEqual(other)
    }

    fun docForPages(pages: Set<Int>) = PdfDocument(filename, document.docForPages(pages), pages)

    fun asClassifiedDocument(classification: String) = ClassifiedPdfDocument(filename, document, pages, classification)

    // for unit testing
    fun isDocEqual(other: PdfDocument) = document.pages.map { it } == other.document.pages.map { it }

    override fun hashCode(): Int {
        var result = filename.hashCode()
        result = 31 * result + document.hashCode()
        result = 31 * result + pages.hashCode()
        return result
    }

    companion object {
        const val LOCK = "Lock"
    }

}