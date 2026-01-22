package com.goldberg.law.script.maritalinvestments.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.clean
import java.math.BigDecimal

data class TransactionLog @JsonCreator constructor(
    @JsonProperty("transaction") val transaction: InvestmentTransaction,
    @JsonProperty("preMaritalPercentage") val preMaritalPercentage: BigDecimal?
) {
    @get:JsonProperty("preMaritalAmount")
    val preMaritalAmount: BigDecimal? by lazy(LazyThreadSafetyMode.NONE) {
        preMaritalPercentage?.let { (transaction.amount * preMaritalPercentage).clean() }
    }
    
    @get:JsonProperty("sharedAmount")
    val sharedAmount: BigDecimal? by lazy(LazyThreadSafetyMode.NONE) {
        preMaritalAmount?.let { (transaction.amount - it).clean() }
    }

    @get:JsonProperty("preMaritalQuantity")
    val preMaritalQuantity: BigDecimal? by lazy(LazyThreadSafetyMode.NONE) {
        if (transaction.quantity != null && preMaritalPercentage != null) {
            (transaction.quantity!! * preMaritalPercentage).clean()
        } else null
    }

    @get:JsonProperty("sharedQuantity")
    val sharedQuantity: BigDecimal? by lazy(LazyThreadSafetyMode.NONE) {
        preMaritalQuantity?.let { (transaction.quantity!! - it).clean() }
    }
}