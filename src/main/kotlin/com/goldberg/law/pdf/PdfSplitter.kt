package com.goldberg.law.pdf

import com.goldberg.law.pdf.model.PdfDocument
import io.github.oshai.kotlinlogging.KotlinLogging

class PdfSplitter {
    private val logger = KotlinLogging.logger {}

    fun splitPdf(input: PdfDocument, pagesDesired: List<Int>, isSeparate: Boolean): List<PdfDocument> =
        if (isSeparate) {
            input.document.pages.mapIndexedNotNull { idx, page ->
                val pageNum = idx + 1
                if (pageNum in pagesDesired) {
                    input.docFromPage(pageNum).also { logger.debug { "Including page $pageNum" } }
                } else {
                    null
                }
            }
        } else {
            listOf(input.docFromPages(pagesDesired))
        }
}