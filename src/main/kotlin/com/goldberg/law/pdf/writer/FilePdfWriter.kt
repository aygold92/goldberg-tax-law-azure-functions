package com.goldberg.law.pdf.writer

import org.apache.pdfbox.pdmodel.PDDocument

class FilePdfWriter: PdfWriter() {
    override fun writePdf(document: PDDocument, fileName: String) {
        document.save(fileName)
    }
}