package com.goldberg.law.function.model.updatemodel

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.document.model.pdf.ClassifiedPdfMetadata
import java.math.BigDecimal

data class TransactionDetails @JsonCreator constructor(
    @JsonProperty("id") val id: String,
    @JsonProperty("date") val date: String,
    @JsonProperty("description") val description: String,
    @JsonProperty("amount") val amount: BigDecimal,
    @JsonProperty("checkNumber") val checkNumber: Int?,
    @JsonProperty("checkPdfMetadata") val checkPdfMetadata: ClassifiedPdfMetadata?,
    @JsonProperty("filePageNumber") val filePageNumber: Int,
)