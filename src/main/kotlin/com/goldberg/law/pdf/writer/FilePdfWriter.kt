package com.goldberg.law.pdf.writer

import com.goldberg.law.pdf.model.PdfDocument
import io.github.oshai.kotlinlogging.KotlinLogging

class FilePdfWriter: PdfWriter() {
    private val logger = KotlinLogging.logger {}

    override fun writePdf(document: PdfDocument, outputDirectory: String) {
        val filename = document.nameWithPage
        val file = "$outputDirectory/$filename.pdf"
        document.document.save(file).also { logger.info { "Saving file $file" } }
    }
}