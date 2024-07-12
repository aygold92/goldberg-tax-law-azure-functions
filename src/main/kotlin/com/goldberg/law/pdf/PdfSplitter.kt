package com.goldberg.law.pdf

import com.goldberg.law.pdf.model.PdfDocument
import org.apache.pdfbox.pdmodel.PDDocument

class PdfSplitter {
    fun splitPdf(input: PdfDocument, pagesDesired: List<Int>, isSeparate: Boolean): List<PdfDocument> {
        if (isSeparate) {
            return input.document.pages.mapIndexedNotNull { idx, page ->
                val pageNum = idx + 1
                if (pageNum in pagesDesired) {
                    println("Including page $pageNum")
                    PdfDocument("${input.name}[$pageNum]", PDDocument().apply { addPage(page) }, listOf(pageNum))
                } else {
                    null.also { println("Not saving page $pageNum") }
                }
            }
        } else {
            val outputDoc = PDDocument()
            input.document.pages.forEachIndexed { idx, page ->
                if ((idx + 1) in pagesDesired) outputDoc.addPage(page).also { println("Including page ${idx + 1}") }
            }
            return listOf(PdfDocument(input.name, outputDoc, pagesDesired))
        }
    }
}