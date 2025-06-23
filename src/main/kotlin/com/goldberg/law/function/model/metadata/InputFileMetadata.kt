package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

/**
 * totalpages is lowercase because Azure storage metadata has super weird behavior of *sometimes* lowercasing the
 * key names depending on which API it is (SetMetadata lowercases but UploadBlob does not).
 */
data class InputFileMetadata @Inject constructor(
    @JsonProperty(NUM_STATEMENTS) val numstatements: Int? = null,
    @JsonProperty(CLASSIFIED) val classified: Boolean = false,
    @JsonProperty(ANALYZED) val analyzed: Boolean = false,
    @JsonProperty(STATEMENTS) val statements: Set<String>? = null,
) {
    // azure blob tags cannot include brackets
    // https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.specialized.blobbaseclient.settags
    fun toMap(): Map<String, String?> = mapOf(
        NUM_STATEMENTS to numstatements?.toString(),
        CLASSIFIED to classified.toString(),
        ANALYZED to analyzed.toString(),
        STATEMENTS to statements?.toString()
            ?.replace("[", "")
            ?.replace("]", "")
    ).filter { it.value != null }

    companion object {
        const val NUM_STATEMENTS = "numstatements"
        const val CLASSIFIED = "classified"
        const val ANALYZED = "analyzed"
        const val STATEMENTS = "statements"
    }
}