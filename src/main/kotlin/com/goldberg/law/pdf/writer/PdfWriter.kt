package com.goldberg.law.pdf.writer

import com.goldberg.law.pdf.model.PdfDocument
import org.apache.pdfbox.pdmodel.PDDocument

abstract class PdfWriter {
    abstract fun writePdf(document: PdfDocument, outputDirectory: String = "./")
}