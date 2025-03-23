package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty

data class CheckDataKey @JsonCreator constructor(
    @JsonProperty("accountNumber") val accountNumber: String?,
    @JsonProperty("checkNumber") val checkNumber: Int?
) {
    override fun toString() = "$accountNumber - $checkNumber"

    @JsonIgnore
    fun isComplete(): Boolean = listOf(accountNumber, checkNumber).all{ it != null }
}