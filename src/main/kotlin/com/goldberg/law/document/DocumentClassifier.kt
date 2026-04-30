package com.goldberg.law.document

import com.goldberg.law.document.model.pdf.PdfDocument
import com.goldberg.law.entity.ClassifiedFile

abstract class DocumentClassifier {
    abstract fun classifyDocument(document: PdfDocument): ClassifiedFile
}
