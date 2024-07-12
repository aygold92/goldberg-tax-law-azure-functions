package com.goldberg.law.pdf.writer

import org.apache.pdfbox.pdmodel.PDDocument

abstract class PdfWriter {
    abstract fun writePdf(document: PDDocument, fileName: String)
}