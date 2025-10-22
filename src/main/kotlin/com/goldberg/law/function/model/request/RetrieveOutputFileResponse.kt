/**
 * Response model for RetrieveOutputFile API endpoint.
 * 
 * Contains the retrieved file content and metadata.
 */

package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import javax.inject.Inject

data class RetrieveOutputFileResponse @Inject constructor(
    @JsonProperty("status") val status: String,
    @JsonProperty("fileName") val fileName: String? = null,
    @JsonProperty("content") val content: String? = null,
    @JsonProperty("errorMessage") val errorMessage: String? = null
)
