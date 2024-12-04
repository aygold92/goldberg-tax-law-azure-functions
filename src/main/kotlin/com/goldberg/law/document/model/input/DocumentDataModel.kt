package com.goldberg.law.document.model.input

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.pdf.PdfDocumentPageMetadata

open class DocumentDataModel(
    @JsonIgnore @Transient open val pageMetadata: PdfDocumentPageMetadata
) {
    @JsonIgnore
    fun isStatement() = pageMetadata.documentType.isStatement()
    @JsonIgnore
    fun isCheck() = pageMetadata.documentType.isCheck()
    @JsonIgnore
    fun isExtraPage() = !pageMetadata.documentType.isRelevant()
}