package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData
import com.goldberg.law.util.addQuotes

/**
 * DocumentType is calculated based on the classification, so we need to code it as a getter, otherwise it will
 * get deserialized as null
 */
@JsonIgnoreProperties("documentType")
data class PdfDocumentPageMetadata @JsonCreator constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("page") val page: Int,
    @JsonProperty("classification") val classification: String
) {
    fun toPdfPageData() = PdfPageData(filename, page)
    @JsonIgnore @Transient
    private var _documentType: DocumentType? = null

    val documentType: DocumentType
        get() {
            if (_documentType == null) {
                _documentType = DocumentType.getBankType(classification)
            }
            return _documentType!!
        }


    fun toCsv() = listOf(
        filename.addQuotes(),
        page
    ).joinToString(",")

    override fun toString() = "{filename: $filename, page: $page, classification: $classification, statementType: ${documentType}}"
}