package com.goldberg.law.function.model.updatemodel

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import java.math.BigDecimal

data class StatementDetails @JsonCreator constructor(
    @JsonProperty("filename") val filename: String,
    @JsonProperty("classification") val classification: String,
    @JsonProperty("statementDate") val statementDate: String,
    @JsonProperty("accountNumber") val accountNumber: String,
    @JsonProperty("beginningBalance") val beginningBalance: BigDecimal,
    @JsonProperty("endingBalance") val endingBalance: BigDecimal,
    @JsonProperty("interestCharged") val interestCharged: BigDecimal?,
    @JsonProperty("feesCharged") val feesCharged: BigDecimal?,
)