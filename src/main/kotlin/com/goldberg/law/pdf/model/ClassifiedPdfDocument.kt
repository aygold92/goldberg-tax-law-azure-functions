package com.goldberg.law.pdf.model

import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocument(name: String, document: PDDocument, pages: List<Int>, val classification: String): PdfDocument(name, document, pages) {
    val statementType = StatementType.getBankType(classification)

    override fun toString() = "{name: $name, pages: $pages, classification: $classification, statementType: $statementType}"

    fun isRelevant() = classification != IRRELEVANT_TYPE

    companion object {
        const val IRRELEVANT_TYPE = "Extra Pages"
    }
}