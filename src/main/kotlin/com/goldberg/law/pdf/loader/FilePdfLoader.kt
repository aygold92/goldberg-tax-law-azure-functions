package com.goldberg.law.pdf.loader

import io.github.oshai.kotlinlogging.KotlinLogging
import com.goldberg.law.document.exception.InvalidArgumentException
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.util.withoutPdfExtension
import org.apache.pdfbox.Loader
import java.io.File

class FilePdfLoader: PdfLoader() {
    private val logger = KotlinLogging.logger {}

    override fun loadPdf(fileName: String): PdfDocument {
        val file = File(fileName).let { if (it.exists()) it else throw InvalidArgumentException("File $fileName not found") }
        return loadPdf(file)
    }

    // TODO: check if it's a PDF? Note: I believe it will fail with IO exception
    private fun loadPdf(file: File): PdfDocument = PdfDocument(file.name.withoutPdfExtension(), Loader.loadPDF(file)).also {
        logger.debug { "Successfully loaded file ${file.name}" }
    }

    override fun loadPdfs(fileName: String): Collection<PdfDocument> {
        val input = File(fileName).let { if (it.exists()) it else throw InvalidArgumentException("File $fileName not found") }

        return if (input.isFile) {
            setOf(loadPdf(input))
        } else if (input.isDirectory) {
            // note: not recursive
            input.listFiles()?.mapNotNull { file ->
                if (file.isDirectory) {
                    logger.debug { "skipping ${file.name} as it is a directory" }
                    null
                } else if (file.isFile) {
                    loadPdf(file)
                } else {
                    // don't know how it would get here. TODO: check if it's possible
                    null
                }
            } ?: throw InvalidArgumentException("No files in directory $fileName")
        } else {
            // don't know how it would get here
            throw InvalidArgumentException("File $input is neither a file nor directory")
        }
    }
}