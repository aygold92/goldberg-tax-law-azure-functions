package com.goldberg.law.document.model.pdf

import com.goldberg.law.entity.Classification
import com.goldberg.law.entity.IClassification
import org.apache.pdfbox.pdmodel.PDDocument

class ClassifiedPdfDocument(
    val classification: Classification,
    document: PDDocument,
) : PdfDocument(classification.inputFile, document), IClassification by classification {

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

    override fun toString() = "{filename: $fileName, pages: $pagesOrdered, classification: $classification, statementType: ${documentType}}"
}