package com.goldberg.law.script.maritalinvestments.model

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.goldberg.law.util.clean
import java.math.BigDecimal

data class TreasuryCoupon @JsonCreator constructor(
    @JsonProperty("name") val name: String,
    @JsonProperty("rate") val rate: BigDecimal?,
    @JsonProperty("mtd") val mtd: String,
    @JsonProperty("dtd") val dtd: String
): InstrumentKey {

    // does not include rate, as it isn't included sometimes
    override fun equals(other: Any?): Boolean {
        return other != null && other is TreasuryCoupon &&
                name == other.name &&
                mtd == other.mtd &&
                dtd == other.dtd &&
                (other.rate == null || rate == null || rate.compareTo(other.rate) == 0)
    }

    override fun hashCode(): Int =
        31 * (31 * name.hashCode() + mtd.hashCode()) + dtd.hashCode()

    override fun toString(): String {
        val rateString = if (rate != null) "${rate} % " else ""
        return "$name CPN ${rateString}MTD $mtd DTD $dtd"
    }

    companion object {
        fun parse(input: String): TreasuryCoupon {
//            val regex = Regex(
//                """^(?<name>.+?)\s+CPN\s+(?<rate>\d+\.\d+)\s+%\s+MTD\s+(?<mtd>\d{4}-\d{2}-\d{2})\s+DTD\s+(?<dtd>\d{4}-\d{2}-\d{2})$"""
//            )
            val regex = Regex(
                """^(?<name>.+?)\s+CPN(?:\s+(?<rate>\d+\.\d+)\s+%)?\s+MTD\s+(?<mtd>\d{4}-\d{2}-\d{2})\s+DTD\s+(?<dtd>\d{4}-\d{2}-\d{2})$"""
            )

            val match = regex.matchEntire(input)
                ?: throw IllegalArgumentException("Invalid treasury coupon format")

            return TreasuryCoupon(
                name = match.groups["name"]!!.value.trim(),
                rate = match.groups["rate"]?.value?.toBigDecimal()?.clean(),
                mtd = match.groups["mtd"]!!.value,
                dtd = match.groups["dtd"]!!.value
            )
        }
    }
}