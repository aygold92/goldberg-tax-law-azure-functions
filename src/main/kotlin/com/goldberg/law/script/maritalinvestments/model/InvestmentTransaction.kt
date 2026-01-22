package com.goldberg.law.script.maritalinvestments.model

import com.fasterxml.jackson.core.JsonParser
import com.fasterxml.jackson.databind.DeserializationContext
import com.fasterxml.jackson.databind.JsonDeserializer
import com.fasterxml.jackson.databind.JsonNode
import java.math.BigDecimal
import java.util.Date

interface InvestmentTransaction {
    val date: Date
    val symbol: InstrumentKey
    val name: String
    val type: TransactionType
    val quantity: BigDecimal?
    val amount: BigDecimal

    fun log(preMaritalPercent: BigDecimal?) = TransactionLog(this, preMaritalPercent)
}

class InvestmentTransactionDeserializer : JsonDeserializer<InvestmentTransaction>() {
    override fun deserialize(p: JsonParser, ctxt: DeserializationContext): InvestmentTransaction {
        val node: JsonNode = p.codec.readTree(p)

        return p.codec.treeToValue(node, VanguardTransaction::class.java)
    }
}