package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData

data class AzureAnalyzeDocumentsRequest @JsonCreator constructor(
    @JsonProperty("documents") val documents: Set<String>,
    @JsonProperty("classifiedTypeOverrides") val classifiedTypeOverrides: Set<ClassifiedTypeOverrides>? = null,
    @JsonProperty("overwrite") val overwrite: Boolean = false,
    @JsonProperty("outputDirectory") val outputDirectory: String = "",
    @JsonProperty("outputFile") val outputFile: String? = null,
    @JsonProperty("outputCsv") val outputCsv: Boolean = false
) {
    fun findClassifiedTypeOverride(pdfPageData: PdfPageData): String? = findClassifiedTypeOverride(pdfPageData.name, pdfPageData.page)
    fun findClassifiedTypeOverride(filename: String, pageNum: Int): String? =
        classifiedTypeOverrides?.find { it.fileName == filename }?.typesOverrides?.find { it.pages.contains(pageNum) }?.type
}