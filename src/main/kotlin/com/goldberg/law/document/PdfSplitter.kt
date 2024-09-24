package com.goldberg.law.document

import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.util.docForPage
import com.goldberg.law.util.withoutExtension
import org.apache.pdfbox.pdmodel.PDDocument

class PdfSplitter {
    fun splitPdf(fileName: String, input: PDDocument, pagesRequested: List<Int>?): List<PdfDocumentPage> {
        val desiredPages = pagesRequested ?: (1..input.numberOfPages).toList() // default to all pages if null
        desiredPages.filter { it > input.pages.count }.takeIf { it.isNotEmpty() }?.let {
            throw IllegalArgumentException("Requesting nonexistent pages $it from document $fileName")
        }
        return input.pages.mapIndexedNotNull { idx, _ ->
            val pageNum = idx + 1
            if (pageNum in desiredPages) {
                PdfDocumentPage(fileName.withoutExtension(), input.docForPage(pageNum), pageNum)
            } else {
                null
            }
        }.ifEmpty { throw IllegalArgumentException("Request $desiredPages from $fileName returned 0 pages") }
    }
}