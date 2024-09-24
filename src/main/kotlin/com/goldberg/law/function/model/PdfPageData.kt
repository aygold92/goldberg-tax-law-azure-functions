package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

data class PdfPageData @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("page") val page: Int,
) {
    fun nameAndPage() = "$name[$page]"
    fun fileNameWithPage() = "${nameAndPage()}.pdf"
    fun originalFileName() = "$name.pdf"
    fun splitPageFilePath() = listOf(name, fileNameWithPage()).joinToString("/")
}