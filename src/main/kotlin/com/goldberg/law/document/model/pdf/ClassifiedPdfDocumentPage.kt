package com.goldberg.law.document.model.pdf

import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocumentPage(name: String, document: PDDocument, page: Int, val classification: String): PdfDocumentPage(name, document, page) {
    val documentType = DocumentType.getBankType(classification)

    override fun toString() = "{name: $name, pages: $page, classification: $classification, statementType: $documentType}"

    fun isRelevant() = classification != IRRELEVANT_TYPE

    fun isStatementDocument() = isRelevant() && !isCheckDocument()

    fun isCheckDocument() = documentType == DocumentType.CHECK

    fun toDocumentMetadata(): PdfDocumentPageMetadata = PdfDocumentPageMetadata(name, page, classification)

    companion object {
        const val IRRELEVANT_TYPE = "Extra Pages"
    }
}