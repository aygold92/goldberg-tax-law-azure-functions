package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

/**
 * totalpages is lowercase because Azure storage metadata has super weird behavior of *sometimes* lowercasing the
 * key names depending on which API it is (SetMetadata lowercases but UploadBlob does not).
 */
data class InputFileMetadata @Inject constructor(
    @JsonProperty("split") val split: Boolean = false,
    @JsonProperty("totalpages") val totalpages: Int? = null,
    @JsonProperty("analyzed") val analyzed: Boolean = false,
    @JsonProperty("statements") val statements: Set<String>? = null,
) {
    // azure blob tags cannot include brackets
    // https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.specialized.blobbaseclient.settags
    fun toMap(): Map<String, String?> = mapOf(
        SPLIT to split.toString(),
        ANALYZED to analyzed.toString(),
        TOTAL_PAGES to totalpages?.toString(),
        STATEMENTS to statements?.toString()
            ?.replace("[", "")
            ?.replace("]", "")
    ).filter { it.value != null }

    companion object {
        const val SPLIT = "split"
        const val ANALYZED = "analyzed"
        const val TOTAL_PAGES = "totalpages"
        const val STATEMENTS = "statements"
    }
}