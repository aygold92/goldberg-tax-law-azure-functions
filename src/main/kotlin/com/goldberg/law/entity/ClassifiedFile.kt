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
    /** The institution's display name, when an agent identified one; null from the Azure pipeline. */
    val bankName: String? = null,
    /** Page number -> bates stamp for these pages; empty when the pages carry no stamps. */
    val batesStamps: Map<Int, String> = emptyMap(),
) {
    @get:JsonIgnore
    val pagesOrdered: List<Int> get() = pages.sorted()

    fun withFileId(fileId: UUID) = ClassifiedFilePages(fileId, pages, classification, bankName, batesStamps)
}

data class ClassifiedFilePages(
    val fileId: UUID,
    val pages: Set<Int>,
    val classification: String,
    val bankName: String? = null,
    val batesStamps: Map<Int, String> = emptyMap(),
) {
    @get:JsonIgnore
    val pagesOrdered: List<Int> get() = pages.sorted()
}