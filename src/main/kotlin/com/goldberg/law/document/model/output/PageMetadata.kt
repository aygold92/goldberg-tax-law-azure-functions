package com.goldberg.law.document.model.output

import com.goldberg.law.util.addQuotes

data class PageMetadata(
    val batesStamp: String? = null, // ID number at the bottom of every submitted page, for tracking purposes
    val filename: String,
    val filePageNumber: Int,
) {
    fun toCsv() = listOf(
        batesStamp?.addQuotes(), filename.addQuotes(), filePageNumber
    ).joinToString(",") { it?.toString() ?: "" }
}