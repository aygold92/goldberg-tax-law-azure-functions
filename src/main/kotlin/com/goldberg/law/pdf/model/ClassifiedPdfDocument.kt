package com.goldberg.law.pdf.model

import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocument(name: String, document: PDDocument, pages: List<Int>, val type: String): PdfDocument(name, document, pages) {
    override fun toString() = "{name: $name, document: $document, pages: $pages, type: $type}"

    fun isTransactionHistory() = type == TRANSACTION_HISTORY_TYPE

    companion object {
        const val TRANSACTION_HISTORY_TYPE = "WF Transaction History"
    }
}