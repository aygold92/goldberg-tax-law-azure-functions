package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

// Request model for deleting a specific bank statement

data class DeleteStatementRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("classification") val classification: String,
    @JsonProperty("date") val date: String?,
    @JsonProperty("filenameWithPages") val filenameWithPages: String?
)
