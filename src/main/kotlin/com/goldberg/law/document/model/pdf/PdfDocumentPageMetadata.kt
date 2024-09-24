package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.addQuotes


data class PdfDocumentPageMetadata @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("page") val page: Int,
    @JsonProperty("classification") val classification: String
) {
    @JsonIgnore @Transient
    val documentType = DocumentType.getBankType(classification)

    fun toCsv() = listOf(
        name.addQuotes(),
        page
    ).joinToString(",")

    override fun toString() = "{name: $name, page: $page, classification: $classification, statementType: $documentType}"
}