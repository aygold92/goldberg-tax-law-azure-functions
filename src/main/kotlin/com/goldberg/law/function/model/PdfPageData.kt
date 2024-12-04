package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.withoutExtension

data class PdfPageData @JsonCreator constructor(
    @JsonProperty("fileName") val fileName: String,
    @JsonProperty("page") val page: Int,
) {
    private fun documentName() = fileName.withoutExtension()
    private fun nameWithPage() = "${documentName()}[$page]"
    private fun fileNameWithPage() = "${nameWithPage()}.pdf"
    fun modelFileName() = "${documentName()}/${nameWithPage()}_Model.json"
    fun splitPageFilePath() = "${documentName()}/${fileNameWithPage()}"
}