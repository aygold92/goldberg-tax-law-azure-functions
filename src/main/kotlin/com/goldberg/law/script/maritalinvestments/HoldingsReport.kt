package com.goldberg.law.script.maritalinvestments

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import com.goldberg.law.script.maritalinvestments.model.InstrumentKey
import com.goldberg.law.script.maritalinvestments.model.InstrumentKeyKeyDeserializer
import com.goldberg.law.util.clean
import java.math.BigDecimal

data class HoldingsReport @JsonCreator constructor(
    @JsonProperty("preMaritalHoldings")
    @JsonDeserialize(keyUsing = InstrumentKeyKeyDeserializer::class)
    val preMaritalHoldings: Map<InstrumentKey, BigDecimal>,

    @JsonProperty("sharedHoldings")
    @JsonDeserialize(keyUsing = InstrumentKeyKeyDeserializer::class)
    val sharedHoldings: Map<InstrumentKey, BigDecimal>,
) {
    @get:JsonProperty("totalHoldings")
    val totalHoldings: Map<InstrumentKey, BigDecimal> by lazy(LazyThreadSafetyMode.NONE) {
        (preMaritalHoldings.keys + sharedHoldings.keys).associateWith { key ->
            ((preMaritalHoldings[key] ?: BigDecimal.ZERO) + (sharedHoldings[key] ?: BigDecimal.ZERO)).clean()
        }
    }
}