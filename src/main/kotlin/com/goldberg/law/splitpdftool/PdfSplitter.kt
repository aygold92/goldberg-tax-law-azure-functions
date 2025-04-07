package com.goldberg.law.splitpdftool

import com.goldberg.law.document.model.pdf.PdfDocumentPage
import com.goldberg.law.util.docForPage
import org.apache.pdfbox.pdmodel.PDDocument

class PdfSplitter {
    fun splitPdf(fileName: String, input: PDDocument, pagesRequested: List<Int>, isSeparate: Boolean): List<PdfDocumentPage> {
        val desiredPages = pagesRequested.ifEmpty { (1..input.numberOfPages).toList() } // default to all pages if empty
        desiredPages.filter { it > input.pages.count }.takeIf { it.isNotEmpty() }?.let {
            throw IllegalArgumentException("Requesting nonexistent pages $it from document $fileName")
        }
        return input.pages.mapIndexedNotNull { idx, _ ->
            val pageNum = idx + 1
            if (pageNum in desiredPages) {
                PdfDocumentPage(fileName, input.docForPage(pageNum), pageNum)
            } else {
                null
            }
        }.ifEmpty { throw IllegalArgumentException("Request $desiredPages from $fileName returned 0 pages") }
    }
}