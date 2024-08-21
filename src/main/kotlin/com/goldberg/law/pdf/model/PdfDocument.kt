package com.goldberg.law.pdf.model

import com.azure.core.util.BinaryData
import io.github.oshai.kotlinlogging.KotlinLogging
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayOutputStream

open class PdfDocument(val name: String, val document: PDDocument, val pages: List<Int>) {
    private val logger = KotlinLogging.logger {}

    constructor(name: String, document: PDDocument): this(name, document, (1..document.numberOfPages).toList())

    init {
        if (document.numberOfPages != pages.size) {
            throw IllegalStateException("Invalid state for PDF document '$name': underlying doc has ${document.numberOfPages} pages but the listed pages show ${pages.size} pages ($pages)")
        }
    }

    val firstPage: Int = pages.first()
    val lastPage: Int = pages.last()
    val nameWithPage = if (isSinglePage()) "$name[$firstPage]" else "$name[$firstPage-$lastPage]"

    fun isSinglePage() = pages.size == 1

    fun toBinaryData(): BinaryData {
        val outputStream = ByteArrayOutputStream()
        document.save(outputStream)
        return BinaryData.fromBytes(outputStream.toByteArray())
    }

    fun withType(type: String): ClassifiedPdfDocument {
        return ClassifiedPdfDocument(name, document, pages, type)
    }

    fun docFromPage(pageNumber: Int): PdfDocument {
        if (this.document.pages.count < pageNumber) throw IllegalStateException("Requesting nonexistent page $pageNumber from document $this")

        val newPDDoc = PDDocument().apply { addPage(this@PdfDocument.document.getPage(pageNumber - 1)) }
        return PdfDocument(this.name, newPDDoc, listOf(pageNumber))
    }

    fun docFromPages(pagesDesired: List<Int>): PdfDocument {
        pagesDesired.forEach {
            if (this.document.pages.count < it || it < 1) throw IllegalStateException("Requesting nonexistent page $it from document $this")
        }
        val outputDoc = PDDocument()
        this.document.pages.forEachIndexed { idx, page ->
            if ((idx + 1) in pagesDesired) outputDoc.addPage(page).also { logger.debug{"Including page ${idx + 1}"} }
        }
        return PdfDocument(this.name, outputDoc, pagesDesired)
    }

    override fun toString() = "{name: $name, pages: $pages}"
}