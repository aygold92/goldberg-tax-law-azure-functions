package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty

// Request model for loading a specific bank statement

data class LoadBankStatementRequest @JsonCreator constructor(
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("classification") val classification: String,
    @JsonProperty("date") val date: String?
) 