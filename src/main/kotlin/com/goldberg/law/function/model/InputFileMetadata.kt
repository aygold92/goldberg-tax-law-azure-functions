package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class InputFileMetadata @Inject constructor(
    @JsonProperty("split") val split: Boolean,
    @JsonProperty("analyzed") val analyzed: Boolean,
    @JsonProperty("statements") val statements: Collection<String>,
) {
    fun toMap(): Map<String, String> = mapOf(
        "split" to split.toString(),
        "analyzed" to analyzed.toString(),
        "statements" to statements.toString()
    )
}