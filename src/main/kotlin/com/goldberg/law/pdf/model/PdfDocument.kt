package com.goldberg.law.pdf.model

import com.azure.core.util.BinaryData
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

open class PdfDocument(val name: String, val document: PDDocument, val pages: List<Int>) {

    constructor(name: String, document: PDDocument): this(name, document, (1..document.numberOfPages).toList())

    init {
        if (document.numberOfPages != pages.size) {
            throw IllegalStateException("Invalid state for PDF document '$name': underlying doc has ${document.numberOfPages} pages but the listed pages show ${pages.size} pages ($pages)")
        }
    }

    fun toBinaryData(): BinaryData {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        return BinaryData.fromBytes(outputStream.toByteArray())
    }

    fun withType(type: String): ClassifiedPdfDocument {
        return ClassifiedPdfDocument(name, document, pages, type)
    }

    override fun toString() = "{name: $name, document: $document, pages: $pages}"
}