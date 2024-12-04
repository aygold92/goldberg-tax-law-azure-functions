package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.goldberg.law.util.SetWithoutBracesDeserializer
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
        "split" to split.toString(),
        "analyzed" to analyzed.toString(),
        "totalpages" to totalpages?.toString(),
        "statements" to statements?.toString()
            ?.replace("[", "")
            ?.replace("]", "")
    ).filter { it.value != null }
}