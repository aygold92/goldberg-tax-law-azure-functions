package com.goldberg.law.pdf.loader

import io.github.oshai.kotlinlogging.KotlinLogging
import com.goldberg.law.document.exception.InvalidArgumentException
import com.goldberg.law.pdf.model.PdfDocument
import com.goldberg.law.util.withoutPdfExtension
import org.apache.pdfbox.Loader
import java.io.File
import java.io.FileInputStream

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

        return if (input.isPdfFile()) {
            setOf(loadPdf(input))
        } else if (input.isDirectory) {
            logger.debug { "reading directory ${input.absolutePath}" }
            // note: not recursive
            input.listFiles()?.mapNotNull { file ->
                if (file.isDirectory) {
                    logger.debug { "skipping ${file.name} as it is a directory" }
                    null
                } else if (file.isPdfFile()) {
                    loadPdf(file)
                } else {
                    logger.debug { "skipping ${file.name} as it is not a PDF" }
                    null
                }
            } ?: throw InvalidArgumentException("No PDF files in directory $fileName")
        } else {
            throw InvalidArgumentException("File $input is neither a PDF file nor directory")
        }
    }

    fun File.isPdfFile(): Boolean {
        return this.isFile && FileInputStream(this).use { inputStream ->
            val headerBytes = ByteArray(5)
            inputStream.read(headerBytes) == 5 && String(headerBytes) == "%PDF-"
        }
    }
}