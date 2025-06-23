package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.addQuotes

/**
 * DocumentType is calculated based on the classification, so we need to code it as a getter, otherwise it will
 * get deserialized as null
 */
@JsonIgnoreProperties("documentType", "pagesOrdered")
data class ClassifiedPdfMetadata @JsonCreator constructor(
    @JsonProperty("filename") override val filename: String,
    @JsonProperty("pages") override val pages: Set<Int>,
    @JsonProperty("classification") val classification: String
): IPdfMetadata {
    constructor(filename: String, page: Int, classification: String): this(filename, setOf(page), classification)
    @JsonIgnore
    @Transient
    private var _documentType: DocumentType? = null

    val documentType: DocumentType
        get() {
            if (_documentType == null) {
                _documentType = DocumentType.getBankType(classification)
            }
            return _documentType!!
        }

    @JsonIgnore
    @Transient
    private var _pagesOrdered: List<Int>? = null

    val pagesOrdered: List<Int>
        get() {
            if (_pagesOrdered == null) {
                _pagesOrdered = pages.sorted()
            }
            return _pagesOrdered!!
        }

    override fun firstPage(): Int = pagesOrdered.first()
    override fun lastPage(): Int = pagesOrdered.last()

    fun toCsv() = listOf(
        filename.addQuotes(),
        pages
    ).joinToString(",")

    override fun toString() = "{filename: $filename, pages: $pages, classification: $classification, statementType: ${documentType}}"
}