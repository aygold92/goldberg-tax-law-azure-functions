package com.goldberg.law.function.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.datamanager.StorageLocation
import java.util.*

/**
 * Event Grid event schema POJO as described in:
 * https://learn.microsoft.com/en-us/azure/azure-functions/functions-bindings-event-grid-trigger?tabs=python-v2%2Cisolated-process%2Cnodejs-v4%2Cextensionv3&pivots=programming-language-java
 */
data class EventSchema @JsonCreator constructor(
    @JsonProperty("topic") val topic: String,
    @JsonProperty("subject") val subject: String,
    @JsonProperty("eventType") val eventType: String,
    @JsonProperty("eventTime") val eventTime: Date,
    @JsonProperty("id") val id: String,
    @JsonProperty("dataVersion") val dataVersion: String,
    @JsonProperty("metadataVersion") val metadataVersion: String,
    @JsonProperty("data") val data: StorageBlobData
) {
    fun getStorageLocation(): StorageLocation = StorageLocation.fromUrl(data.url)
}

data class StorageBlobData @JsonCreator constructor(
    @JsonProperty("api") val api: String,
    @JsonProperty("clientRequestId") val clientRequestId: String,
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("eTag") val eTag: String,
    @JsonProperty("contentType") val contentType: String,
    @JsonProperty("contentLength") val contentLength: Long,
    @JsonProperty("blobType") val blobType: String, 
    @JsonProperty("url") val url: String,
    @JsonProperty("sequencer") val sequencer: String,
    @JsonProperty("storageDiagnostics") val storageDiagnostics: StorageDiagnostics
)
data class StorageDiagnostics @JsonCreator constructor(
    @JsonProperty("batchId") val batchId: String
)