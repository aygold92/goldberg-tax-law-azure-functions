package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.addQuotes

@JsonIgnoreProperties("pagesOrdered")
open class PdfMetadata @JsonCreator constructor(
    @JsonProperty("filename") override val filename: String,
    @JsonProperty("pages") override val pages: Set<Int>,
): IPdfMetadata {
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
}