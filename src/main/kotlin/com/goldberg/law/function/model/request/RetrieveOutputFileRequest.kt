/**
 * Request model for RetrieveOutputFile API endpoint.
 * 
 * Contains the parameters needed to retrieve a specific output file from Azure Storage.
 */

package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class RetrieveOutputFileRequest @Inject constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("fileName") val fileName: String
)
