package com.goldberg.law.document.model.pdf

import com.fasterxml.jackson.annotation.JsonIgnore
import com.goldberg.law.util.withoutExtension

interface IPdfMetadata {
    val filename: String
    val pages: Set<Int>

    private fun documentName() = filename.substringAfterLast("/").withoutExtension()
    fun nameWithPages() = "${documentName()}[${firstPage()}-${lastPage()}]"
    fun fileNameWithPages() = "${nameWithPages()}.pdf"
    fun classificationFileName() = "${nameWithPages()}_Classification.json"
    fun modelFileName() = "${documentName()}/${nameWithPages()}_Model.json"
    fun splitPageFilePath() = "${documentName()}/${fileNameWithPages()}"

    fun firstPage(): Int
    fun lastPage(): Int

    @JsonIgnore
    fun getPageRange(): Pair<Int, Int> = Pair(firstPage(), lastPage())
}