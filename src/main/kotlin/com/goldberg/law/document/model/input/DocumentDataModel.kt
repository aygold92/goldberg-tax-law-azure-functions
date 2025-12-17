package com.goldberg.law.document.model.input

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.entity.Classification

open class DocumentDataModel(
    @JsonIgnore @Transient open val classification: Classification
) {
    @JsonIgnore
    fun isStatement() = classification.documentType.isStatement()
    @JsonIgnore
    fun isCheck() = classification.documentType.isCheck()
    @JsonIgnore
    fun isExtraPage() = !classification.documentType.isRelevant()
}