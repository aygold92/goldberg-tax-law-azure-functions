package com.goldberg.law.document

import com.goldberg.law.document.model.input.CheckDataModel
import com.goldberg.law.document.model.input.StatementDataModel
import com.goldberg.law.document.model.pdf.ClassifiedPdfDocument

abstract class DocumentDataExtractor {
    abstract fun extractStatementData(classifiedDocument: ClassifiedPdfDocument): StatementDataModel
    abstract fun extractCheckData(classifiedDocument: ClassifiedPdfDocument): CheckDataModel
}
