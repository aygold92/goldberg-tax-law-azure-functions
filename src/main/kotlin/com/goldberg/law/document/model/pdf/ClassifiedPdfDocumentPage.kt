package com.goldberg.law.document.model.pdf

import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocumentPage(name: String, document: PDDocument, page: Int, val classification: String): PdfDocumentPage(name, document, page) {
    val documentType = DocumentType.getBankType(classification)

    override fun toString() = "{fileName: $fileName, page: $page, classification: $classification, statementType: $documentType}"

    fun isRelevant() = documentType.isRelevant()

    fun isStatementDocument() = documentType.isStatement()

    fun isCheckDocument() = documentType.isCheck()

    fun toDocumentMetadata(): PdfDocumentPageMetadata = PdfDocumentPageMetadata(fileName, page, classification)

    companion object {
        const val IRRELEVANT_TYPE = "Extra Pages"
    }
}