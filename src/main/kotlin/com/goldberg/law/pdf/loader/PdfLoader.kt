package com.goldberg.law.pdf.loader

import com.goldberg.law.pdf.model.PdfDocument

abstract class PdfLoader {
    abstract fun loadPdfs(fileName: String): Collection<PdfDocument>

    fun String.withoutPdfExtension() = removeSuffix(".pdf")
}