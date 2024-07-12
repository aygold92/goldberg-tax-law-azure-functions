package com.goldberg.law.pdf.loader

import com.goldberg.law.document.exception.InvalidArgumentException
import com.goldberg.law.pdf.model.PdfDocument
import org.apache.pdfbox.Loader
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class FilePdfLoader: PdfLoader() {
    override fun loadPdfs(fileName: String): Collection<PdfDocument> {
        val input = File(fileName).let { if (it.exists()) it else throw InvalidArgumentException("File $fileName not found") }

        return if (input.isFile) {
            // TODO: check if it's a PDF? Note: I believe it will fail with IO exception
            setOf(PdfDocument(fileName.withoutPdfExtension(), Loader.loadPDF(input))).also {
                println("Successfully loaded file $fileName")
            }
        } else if (input.isDirectory) {
            input.listFiles()?.mapNotNull { file ->
                if (file.isDirectory) {
                    println("skipping ${file.name} as it is a directory")
                    null
                } else if (file.isFile) {
                    // TODO: check if it's a PDF?
                    PdfDocument(file.name.withoutPdfExtension(), Loader.loadPDF(input)).also {
                        println("Successfully loaded file $fileName")
                    }
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