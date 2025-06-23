package com.goldberg.law.function.model.request

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.PdfMetadata

data class LoadTransactionsFromModelRequest @JsonCreator constructor(
    @JsonProperty("requestId") val requestId: String,
    @JsonProperty("clientName") val clientName: String,
    @JsonProperty("pdfMetadata") val pdfMetadata: PdfMetadata,
    @JsonProperty("statementDate") val statementDate: String,
)