package com.goldberg.law.pdf

import org.apache.pdfbox.pdmodel.PDDocument
import java.util.*

class PDFSplitter {
    fun splitPdf(req: SplitPdfRequest): List<PDDocument> {
        val document = PDF_LOADER.loadPdf(req.inputFile)

        val range: Set<Int> = req.range?.first?.rangeTo(req.range.second)?.toSet()
            .let { it ?: Collections.emptySet() }

        val pages: Set<Int> = req.pages?.map { it - 1 }?.toSet()
            .let { it ?: Collections.emptySet() }

        val combinedPages = range + pages

        return if (req.isSeparate) {
            document.pages.mapIndexedNotNull { idx, page ->
                if (idx in combinedPages) PDDocument().also { it.addPage(page) } else null
            }
        } else {
            listOf(PDDocument().also { outputDoc ->
                document.pages.forEachIndexed { idx, page -> if (idx in combinedPages) outputDoc.addPage(page) }
            })
        }
    }

    companion object {
        private val PDF_LOADER = PdfLoader()
    }
}