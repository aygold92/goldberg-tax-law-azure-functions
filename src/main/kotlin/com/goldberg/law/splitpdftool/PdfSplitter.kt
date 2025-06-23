package com.goldberg.law.splitpdftool

import com.goldberg.law.util.docForPage
import org.apache.pdfbox.pdmodel.PDDocument

class PdfSplitter {

    private fun getDesiredPages(fileName: String, numPages: Int, pagesRequested: List<Int>): List<Int> {
        val desiredPages = pagesRequested.ifEmpty { (1..numPages).toList() } // default to all pages if empty
        desiredPages.filter { it > numPages }.takeIf { it.isNotEmpty() }?.let {
            throw IllegalArgumentException("Requesting nonexistent pages $it from document $fileName")
        }
        return desiredPages
    }

    fun splitPdf(fileName: String, input: PDDocument, pagesRequested: List<Int>): List<PdfDocumentPage> {
        val desiredPages = getDesiredPages(fileName, input.numberOfPages, pagesRequested)

        return input.pages.mapIndexedNotNull { idx, _ ->
            val pageNum = idx + 1
            if (pageNum in desiredPages) {
                PdfDocumentPage(fileName, input.docForPage(pageNum), pageNum)
            } else {
                null
            }
        }.ifEmpty { throw IllegalArgumentException("Request $desiredPages from $fileName returned 0 pages") }
    }

    fun splitPdfOneDocument(fileName: String, input: PDDocument, pagesRequested: List<Int>): PDDocument {
        val desiredPages = getDesiredPages(fileName, input.numberOfPages, pagesRequested)

        val newDoc = PDDocument()
        input.pages.forEachIndexed { idx, _ ->
            val pageNum = idx + 1
            if (pageNum in desiredPages) {
                newDoc.addPage(input.getPage(idx))
            }
        }

        if (newDoc.numberOfPages == 0) throw IllegalArgumentException("Request $desiredPages from $fileName returned 0 pages")
        return newDoc
    }
}