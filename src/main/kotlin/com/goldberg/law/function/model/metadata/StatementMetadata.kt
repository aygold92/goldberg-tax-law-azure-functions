package com.goldberg.law.function.model.metadata

import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class StatementMetadata @Inject constructor(
    @JsonProperty("md5") val md5: String,
    @JsonProperty("suspicious") val suspicious: Boolean,
    @JsonProperty("missingchecks") val missingchecks: Boolean,
    @JsonProperty("manuallyverified") val manuallyverified: Boolean? = null
) {
    // azure blob tags cannot include brackets
    // https://learn.microsoft.com/en-us/dotnet/api/azure.storage.blobs.specialized.blobbaseclient.settags
    fun toMap(): Map<String, String?> = mapOf(
        "md5" to md5,
        "suspicious" to suspicious.toString(),
        "missingchecks" to missingchecks.toString(),
        "manuallyverified" to manuallyverified.toString()
    )
}