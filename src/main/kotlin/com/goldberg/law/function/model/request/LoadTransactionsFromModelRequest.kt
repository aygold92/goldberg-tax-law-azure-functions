package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.function.model.PdfPageData

data class LoadTransactionsFromModelRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("pdfPageData") val pdfPageData: PdfPageData,
    @JsonProperty("statementDate") val statementDate: String,
)