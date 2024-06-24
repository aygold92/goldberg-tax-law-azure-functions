package com.goldberg.law.pdf

import com.fasterxml.jackson.annotation.JsonProperty

data class SplitPdfRequest(
    // @JsonProperty("InputFile")
    val inputFile: String,
    val outputFile: String,
    val range: Pair<Int, Int>?,
    val pages: List<Int>?,
    val isSeparate: Boolean)