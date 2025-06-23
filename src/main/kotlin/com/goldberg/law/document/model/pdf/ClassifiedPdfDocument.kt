package com.goldberg.law.document.model.pdf

import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocument(
    filename: String,
    document: PDDocument,
    pages: Set<Int>,
    val classification: String
) : PdfDocument(filename, document, pages) {
    val documentType = DocumentType.getBankType(classification)

    fun toDocumentMetadata() = ClassifiedPdfMetadata(filename, pages, classification)

    fun isRelevant() = documentType.isRelevant()

    fun isStatementDocument() = documentType.isStatement()

    fun isCheckDocument() = documentType.isCheck()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        if (other !is ClassifiedPdfDocument) return false

        return super.equals(other) && this.classification == other.classification
    }

    override fun hashCode(): Int {
        var result = super.hashCode()
        result = 31 * result + classification.hashCode()
        return result
    }

    override fun toString() = "{filename: $filename, pages: $pages, classification: $classification, statementType: ${documentType}}"
}