package com.goldberg.law.pdf

import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import java.io.File
import java.nio.file.Files
import java.nio.file.Path

class PdfLoader {
    fun loadPdf(fileName: String): PDDocument  {
        Files.exists(Path.of(fileName))

        val file = File(fileName).let { if (it.exists() && it.isFile) it else throw Exception() }
        return Loader.loadPDF(file)
    }
}