package com.goldberg.law.script.maritalinvestments.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.KeyDeserializer
import com.fasterxml.jackson.databind.annotation.JsonDeserialize
import java.math.BigDecimal


sealed interface InstrumentKey

data class HoldingSymbol @JsonCreator constructor(@JsonProperty("name") val name: String): InstrumentKey {

    override fun toString(): String = name

    companion object {
        val NO_SYM = HoldingSymbol("--")
    }
}

class InstrumentKeyKeyDeserializer : KeyDeserializer() {
    override fun deserializeKey(key: String, ctxt: DeserializationContext): Any {
        return if (key.startsWith("U S TREASURY")) {
            TreasuryCoupon.parse(key)
        } else {
            HoldingSymbol(key)
        }
    }
}

class InstrumentKeyDeserializer : JsonDeserializer<InstrumentKey>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InstrumentKey {
        val key = p.text
        return if (key.startsWith("U S TREASURY")) {
            TreasuryCoupon.parse(key)
        } else {
            HoldingSymbol(key)
        }
    }
}

data class HoldingsReport @JsonCreator constructor(
    @JsonProperty("preMaritalHoldings")
    @JsonDeserialize(keyUsing = InstrumentKeyKeyDeserializer::class)
    val preMaritalHoldings: Map<InstrumentKey, BigDecimal>,

    @JsonProperty("sharedHoldings") 
    @JsonDeserialize(keyUsing = InstrumentKeyKeyDeserializer::class)
    val sharedHoldings: Map<InstrumentKey, BigDecimal>,
)