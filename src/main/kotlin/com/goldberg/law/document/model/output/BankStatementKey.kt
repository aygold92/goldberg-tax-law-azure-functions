package com.goldberg.law.document.model.output

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.fromWrittenDate
import com.goldberg.law.util.toTransactionDate
import java.util.Date
import java.util.IllegalFormatException

@JsonIgnoreProperties(ignoreUnknown = true)
data class BankStatementKey @JsonCreator constructor(
    @JsonProperty("statementDate")
    val date: String?,
    @JsonProperty("accountNumber")
    val accountNumber: String?,
    @JsonProperty("classification")
    val classification: String,
) {
    @JsonIgnore @Transient
    val statementDate = fromWrittenDate(date)

    override fun toString() = "$accountNumber:$classification:$date"

    fun isComplete(): Boolean = listOf(statementDate, accountNumber, classification).all{ it != null }

    companion object {
        fun fromString(key: String) = key.split(":").let {
            if (it.size != 3) {
                throw IllegalArgumentException("Invalid statementKey string: $key")
            }
            BankStatementKey(it[2], it[1], it[0])
        }
    }
}