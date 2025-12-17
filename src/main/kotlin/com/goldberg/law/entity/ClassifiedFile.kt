package com.goldberg.law.entity

import com.fasterxml.jackson.annotation.JsonIgnore
import java.util.UUID

data class ClassifiedFile(
    val fileId: UUID,
    val classifications: List<ClassifiedPages>,
)

data class ClassifiedPages(
    val pages: Set<Int>,
    val classification: String,
) {
    @get:JsonIgnore
    val pagesOrdered: List<Int> get() = pages.sorted()

    fun withFileId(fileId: UUID) = ClassifiedFilePages(fileId, pages, classification)
}

data class ClassifiedFilePages(
    val fileId: UUID,
    val pages: Set<Int>,
    val classification: String,
) {
    @get:JsonIgnore
    val pagesOrdered: List<Int> get() = pages.sorted()
}