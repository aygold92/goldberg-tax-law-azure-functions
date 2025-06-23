package com.goldberg.law.document.model.input

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata

open class DocumentDataModel(
    @JsonIgnore @Transient open val pageMetadata: ClassifiedPdfMetadata
) {
    @JsonIgnore
    fun isStatement() = pageMetadata.documentType.isStatement()
    @JsonIgnore
    fun isCheck() = pageMetadata.documentType.isCheck()
    @JsonIgnore
    fun isExtraPage() = !pageMetadata.documentType.isRelevant()
}